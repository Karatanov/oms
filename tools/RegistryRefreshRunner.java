import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Offline operator entry point using the exact import services shipped in OMS.
 * Compile with JDK 21; run with the server distribution's lib/* classpath.
 * Does not manufacture sessions, bypass HTTP permissions or log credentials.
 */
public final class RegistryRefreshRunner {
    private static final String ROOT = "oms.urp3.";
    private static Object unit() throws Exception {
        return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
    }
    private static Object progress() throws Exception {
        Class<?> function = Class.forName("kotlin.jvm.functions.Function2");
        return Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function},
            (proxy, method, args) -> method.getName().equals("invoke") ? unit() : null);
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = Arrays.stream(target.getClass().getMethods())
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length)
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing service method: " + name));
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) {
            throw new IllegalStateException(name + " failed: " + error.getCause().getMessage(), error.getCause());
        }
    }
    private static Object singleton(String name) throws Exception {
        Field instance = Class.forName(ROOT + name).getField("INSTANCE");
        instance.setAccessible(true);
        return instance.get(null);
    }
    private static void connect() throws Exception {
        Class<?> database = Class.forName("org.jetbrains.exposed.v1.jdbc.Database");
        Object companion = database.getField("Companion").get(null);
        Method connect = Arrays.stream(companion.getClass().getMethods())
            .filter(m -> m.getName().equals("connect$default") && m.getParameterCount() == 11)
            .findFirst().orElseThrow();
        connect.invoke(null, companion, System.getenv("DATABASE_URL"), "com.mysql.cj.jdbc.Driver",
            System.getenv("DATABASE_USER"), System.getenv("DATABASE_PASSWORD"),
            null, null, null, null, 240, null);
    }
    private static void await(Object service, Object started) throws Exception {
        String id = (String) invoke(started, "getId");
        long deadline = System.nanoTime() + 600_000_000_000L;
        String previous = "";
        while (System.nanoTime() < deadline) {
            Object status = invoke(service, "refresh", id);
            String state = (String) invoke(status, "getStatus");
            String message = state + " " + invoke(status, "getProgress") + "% " + invoke(status, "getPhase");
            if (!message.equals(previous)) { System.out.println(message); previous = message; }
            if (state.equals("COMPLETED")) return;
            if (!state.equals("RUNNING")) throw new IllegalStateException("Registry refresh: " + invoke(status, "getError"));
            Thread.sleep(250);
        }
        invoke(service, "cancel", id);
        throw new IllegalStateException("Registry refresh timed out; cancellation requested");
    }
    public static void main(String[] args) {
        try {
            if (args.length != 2 || !Set.of("preflight", "apply", "payments").contains(args[0]))
                throw new IllegalArgumentException("Usage: RegistryRefreshRunner preflight|apply|payments SOURCE_DIRECTORY");
            Path directory = Path.of(args[1]);
            byte[] allocation = Files.readAllBytes(directory.resolve("allocation.xlsx"));
            byte[] monitoring = Files.readAllBytes(directory.resolve("monitoring.xlsx"));
            byte[] procurement = Files.readAllBytes(directory.resolve("procurement.xlsx"));
            byte[] payments = Files.readAllBytes(directory.resolve("payments.xlsx"));
            Object parser = singleton("service.MonitoringWorkbookSource");
            System.out.println("Allocation rows: " + ((List<?>) invoke(parser, "parse", allocation, progress())).size());
            System.out.println("Monitoring rows: " + ((List<?>) invoke(parser, "parse", monitoring, progress())).size());
            Object procurementParser = singleton("service.ProcurementWorkbookSource");
            System.out.println("Contract rows: " + ((List<?>) invoke(procurementParser, "parseApprovedWorkbook", procurement, progress())).size());
            Object paymentData = invoke(singleton("service.PaymentMonitoringWorkbookSource"), "parseWorkbookData", payments);
            System.out.println("Payment summary rows: " + ((List<?>) invoke(paymentData, "getItems")).size());
            System.out.println("Payment transactions: " + ((List<?>) invoke(paymentData, "getTransactions")).size());
            if (args[0].equals("preflight")) return;
            connect();
            Object container = singleton("config.AppContainer");
            if (args[0].equals("payments")) {
                Object snapshot = invoke(invoke(container, "getPaymentMonitoringService"), "importWorkbook", payments);
                System.out.println("Payment summaries imported: " + ((List<?>) invoke(snapshot, "getItems")).size());
                System.out.println("Payment refresh completed; other registries unchanged");
                System.exit(0);
            }
            Object registry = invoke(container, "getSubprojectRegistryRefreshService");
            // The allocation supplies missing tranche-B identities first. The
            // fuller operational monitoring source then supplies current facts.
            await(registry, invoke(registry, "startWorkbookRefresh", allocation));
            await(registry, invoke(registry, "startWorkbookRefresh", monitoring));
            Object procurementService = invoke(container, "getProcurementService");
            Class<?> boolFunction = Class.forName("kotlin.jvm.functions.Function0");
            Object noCancel = Proxy.newProxyInstance(boolFunction.getClassLoader(), new Class<?>[]{boolFunction},
                (proxy, method, values) -> method.getName().equals("invoke") ? false : null);
            Object result = invoke(procurementService, "refreshFromApprovedSource", procurement, null, noCancel, progress());
            System.out.println("Contracts imported: " + invoke(result, "getRecords"));
            Object snapshot = invoke(invoke(container, "getPaymentMonitoringService"), "importWorkbook", payments);
            System.out.println("Payment summaries imported: " + ((List<?>) invoke(snapshot, "getItems")).size());
            System.out.println("Refresh completed");
            System.exit(0);
        } catch (Exception error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}

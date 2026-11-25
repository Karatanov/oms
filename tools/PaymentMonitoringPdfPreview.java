import java.nio.file.Files;
import java.nio.file.Path;
import oms.urp3.service.PaymentMonitoringPdfExportService;
import oms.urp3.service.PaymentMonitoringPdfExportServiceTest;

/** Visual QA using the same production exporter and regression fixtures as JUnit. */
class PaymentMonitoringPdfPreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: output-directory");
        var output = Path.of(args[0]);
        Files.createDirectories(output);
        var fixture = new PaymentMonitoringPdfExportServiceTest();
        for (var language : PaymentMonitoringPdfExportService.Language.values()) {
            Files.write(output.resolve("payments-" + language + ".pdf"), fixture.preview(language, 2));
        }
        Files.write(output.resolve("payments-multipage.pdf"), fixture.preview(PaymentMonitoringPdfExportService.Language.EN, 110));
        System.out.println("Payment PDF previews: " + output.toAbsolutePath());
    }
}

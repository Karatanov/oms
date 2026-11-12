import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import oms.urp3.domain.Project;
import oms.urp3.service.ProjectPdfExportService;
import oms.urp3.service.ProjectPdfExportServiceTest;
import oms.urp3.service.ProjectFinancialExecution;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Optional visual QA; run with compiled test classes, server resources and PDFBox on the classpath. */
class ProjectPdfExecutionPreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: output.png");
        var fixture = new ProjectPdfExportServiceTest();
        var method = fixture.getClass().getDeclaredMethod("testSubproject");
        method.setAccessible(true);
        var project = (Project) method.invoke(fixture);
        byte[] pdf = new ProjectPdfExportService().export(project, null,
            ProjectPdfExportService.Language.EN, List.of(), List.of(), List.of(), List.of(), List.of(),
            new ProjectFinancialExecution(125.0, 100.0));
        try (var document = PDDocument.load(pdf)) {
            var image = new PDFRenderer(document).renderImageWithDPI(0, 100);
            if (!ImageIO.write(image, "png", new File(args[0]))) throw new IllegalStateException("PNG writer unavailable");
        }
        System.out.println(args[0]);
    }
}

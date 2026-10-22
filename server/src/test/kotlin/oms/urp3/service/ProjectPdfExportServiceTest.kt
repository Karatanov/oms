package oms.urp3.service

import oms.urp3.domain.Project
import oms.urp3.domain.ProjectStatus
import oms.urp3.domain.ProjectType
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectPdfExportServiceTest {
    @Test
    fun `exports saved inspection thumbnails as localized photo evidence`() {
        val photo = BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB)
        val thumbnail = ByteArrayOutputStream().use { output ->
            check(ImageIO.write(photo, "jpg", output))
            output.toByteArray()
        }

        val pdf = ProjectPdfExportService().export(
            project = testSubproject(),
            language = ProjectPdfExportService.Language.UK,
            inspectionPhotos = listOf(
                ProjectPdfExportService.PhotoEvidence("SIR-001 · 22.10.2026 · roof.jpg", thumbnail)
            )
        )

        assertTrue(pdf.decodeToString(0, 4).startsWith("%PDF"))
        PDDocument.load(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Фото інспекцій"))
            assertTrue(text.contains("SIR-001"))
        }
    }

    private fun testSubproject() = Project(
        id = 1L,
        uuid = UUID.randomUUID(),
        projectType = ProjectType.SUBPROJECT,
        trancheNumber = 1,
        parentProjectId = null,
        name = "Тестовий субпроєкт",
        siteName = "TS01",
        siteNumber = "TS01",
        description = null,
        address = null,
        region = null,
        city = null,
        latitude = null,
        longitude = null,
        status = ProjectStatus.ACTIVE,
        sector = null,
        constructionType = null,
        budgetPlanned = 1_000L,
        engineerConsultantContractAmount = null,
        technicalSupervisionAmount = null,
        subprojectContractAmount = null,
        startDate = null,
        endDate = null,
        contractSignedDate = null,
        plannedEndDate = null,
        constructionContractSigningDate = null,
        constructionStartDate = null,
        projectedCompletionTime = null,
        currency = "UAH",
        contractorName = null
    )
}

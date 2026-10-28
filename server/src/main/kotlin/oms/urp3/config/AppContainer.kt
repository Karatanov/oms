package oms.urp3.config

import oms.urp3.repository.*
import oms.urp3.service.*
import oms.urp3.service.InspectionFindingService

/**
 * Найпростіший контейнер залежностей застосунку.
 *
 * Об'єкти створюються один раз та повторно
 * використовуються всією системою.
 */
object AppContainer {

    /**
     * Шар доступу до даних.
     */
    val roleRepository: RoleRepository =
        ExposedRoleRepository()

    /**
     * Шар бізнес-логіки.
     */
    val roleService =
        RoleService(roleRepository)

    /**
     * Шар доступу до користувачів.
     */
    val userRepository: UserRepository =
        ExposedUserRepository()

    /**
     * Бізнес-логіка роботи з користувачами.
     */
    val userService =
        UserService(
            userRepository,
            roleService
        )

    /**
     * Сервіс автентифікації користувачів.
     */
    val authService =
        AuthService(userService, userRepository)

    val activationService = ActivationService(userRepository)

    /**
     * Репозиторій проєктів.
     */
    val projectRepository: ProjectRepository =
        ExposedProjectRepository()

    /**
     * Бізнес-логіка роботи з проєктами.
     */
    val projectService =
        ProjectService(projectRepository)

    val geocodingService = GeocodingService()

    /**
     * Репозиторій інспекцій.
     */
    val inspectionReportRepository:
            InspectionReportRepository =
        ExposedInspectionReportRepository()

    /**
     * Сервіс роботи з інспекціями.
     */
    val inspectionReportService =
        InspectionReportService(
            inspectionReportRepository
        )

    /**
     * Репозиторій зауважень інспекцій.
     */
    val inspectionFindingRepository:
            InspectionFindingRepository =
        ExposedInspectionFindingRepository()

    /**
     * Сервіс роботи
     * із зауваженнями інспекцій.
     */
    val inspectionFindingService =
        InspectionFindingService(
            inspectionFindingRepository
        )

    val financialRecordRepository: FinancialRecordRepository = ExposedFinancialRecordRepository()
    val nbuExchangeRateService = NbuExchangeRateService()
    val financialRecordService = FinancialRecordService(financialRecordRepository, nbuExchangeRateService)
    val paymentMonitoringRepository: PaymentMonitoringRepository = ExposedPaymentMonitoringRepository()
    val paymentMonitoringService = PaymentMonitoringService(paymentMonitoringRepository)

    val procurementRecordRepository: ProcurementRecordRepository = ExposedProcurementRecordRepository()
    val procurementService = ProcurementService(procurementRecordRepository, projectService, userRepository)
    val subprojectRegistryRefreshService = SubprojectRegistryRefreshService(userRepository, projectService)

    val inspectionReportFileRepository: InspectionReportFileRepository = ExposedInspectionReportFileRepository()
    val inspectionReportFileService = InspectionReportFileService(inspectionReportFileRepository, inspectionReportService)
    val sirPdfExportService = SirPdfExportService(inspectionReportFileService)

    val projectDocumentRepository: ProjectDocumentRepository = ExposedProjectDocumentRepository()
    val projectDocumentService = ProjectDocumentService(projectDocumentRepository)

    val inspectionPhotoRepository: InspectionPhotoRepository = ExposedInspectionPhotoRepository()
    val inspectionPhotoService = InspectionPhotoService(inspectionPhotoRepository)

    val auditLogService = AuditLogService()
    val inspectionAnalyticsService = InspectionAnalyticsService()
    val dashboardService = DashboardService(auditLogService, paymentMonitoringService)
}

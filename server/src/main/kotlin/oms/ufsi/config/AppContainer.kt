package oms.ufsi.config

import oms.ufsi.repository.*
import oms.ufsi.service.*
import oms.ufsi.service.InspectionFindingService

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
        AuthService(userService)

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
    val financialRecordService = FinancialRecordService(financialRecordRepository)

    val inspectionReportFileRepository: InspectionReportFileRepository = ExposedInspectionReportFileRepository()
    val inspectionReportFileService = InspectionReportFileService(inspectionReportFileRepository, inspectionReportService)
}

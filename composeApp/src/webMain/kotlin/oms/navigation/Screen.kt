package oms.navigation

// 🔹 Опис усіх екранів системи
// 🔹 Використовується як єдине джерело правди для навігації
// 🔹 Опис екрану з метаданими (title для UI)
sealed class Screen(val title: String) {
    // 🔹 Екран логіну (без sidebar)
    object Login : Screen("Login")

    // 🔹 Головна панель
    object Dashboard : Screen("Dashboard")

    // 🔹 Список проєктів
    object Projects : Screen("Projects")

    // Створення нового проєкту
    object CreateProject : Screen("Create Project")

    // 🔹 Деталі проєкту
    object ProjectDetail : Screen("Project Detail")

    // 🔹 Створення / редагування інспекції
    object CreateInspection : Screen("Create Inspection")

    // 🔹 Карта
    object Map : Screen("Projects Map")

    // 🔹 Інспекції
    object Inspections : Screen("Inspection Reports")

    // 🔹 Фінанси
    object Financial : Screen("Financial Monitoring")

    // 🔹 Документи
    object Documents : Screen("Documents")

    // 🔹 Адмінка
    object Admin : Screen("Administration")
}

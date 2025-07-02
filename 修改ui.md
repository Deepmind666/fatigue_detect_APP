# 如何手动修改App界面 (UI)

本文档是一个简单的指南，旨在帮助初学者了解如何修改本应用的用户界面（UI）。你将学习如何更改颜色、文字、图标和布局，即使你对安卓开发不太熟悉也没关系。

## 1. UI文件在哪里？

我们应用的所有界面代码都位于 `app/src/main/java/com/example/juicemachine/ui/` 目录下。每个 `.kt` 文件通常对应一个屏幕：

-   `DrinkMenuScreen.kt`: 主屏幕，显示所有果汁饮品。
-   `AdminScreen.kt`: 管理员后台屏幕，用于管理配方。
-   `EditRecipeScreen.kt`: 添加和编辑配方的屏幕。
-   `theme/`: 这个文件夹里的文件定义了应用的整体风格。
    -   `Color.kt`: 定义了应用使用的所有颜色。
    -   `Type.kt`: 定义了应用使用的字体样式（如标题、正文等）。

## 2. 修改主屏幕 (`DrinkMenuScreen.kt`)

打开 `DrinkMenuScreen.kt` 文件，你可以修改以下部分：

### 修改顶部标题

找到 `Header` 这个函数，你可以修改标题文字和背景颜色。

```kotlin
// 在 DrinkMenuScreen.kt 文件中找到 Header 函数

@Composable
fun Header(...) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 👇 修改这里的 .background() 来改变背景颜色
            //    MaterialTheme.colorScheme.primaryContainer 是在 theme/Color.kt 中定义的
            .background(MaterialTheme.colorScheme.primaryContainer) 
            // ...
    ) {
        Text(
            // 👇 修改这里的文字来改变标题
            "智能茶饮系统", 
            // 👇 修改这里的 fontSize 来改变文字大小
            fontSize = 28.sp, 
            // 👇 修改这里的 color 来改变文字颜色
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        // ...
    }
}
```

### 修改功能按钮

找到 `ActionButton` 函数，你可以修改按钮的文字和颜色。

```kotlin
// 在 DrinkMenuScreen.kt 文件中找到 ActionButton 函数

@Composable
fun ActionButton(text: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    Button(
        // ...
        colors = if (isPrimary) 
            // 👇 这是 "开始制作" 按钮的颜色
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) 
        else 
            // 👇 这是其他普通按钮的颜色
            ButtonDefaults.buttonColors(),
        // ...
    ) {
        // 👇 这里的 text 参数是在调用时传入的，例如 "一键清洗"
        Text(text, fontSize = 18.sp) 
    }
}

// 在 DrinkMenuScreen 组件中，你可以看到这些按钮是如何被调用的：
// ActionButton(text = "自动加水", onClick = onAddWater)
// ActionButton(text = "一键清洗", onClick = onClean)
// ActionButton(text = "开始制作", onClick = onMakeJuice, isPrimary = true)
```

### 修改饮品卡片布局

找到 `DrinkCard` 函数，你可以修改卡片上图片的大小和文字样式。

```kotlin
// 在 DrinkMenuScreen.kt 文件中找到 DrinkCard 函数

@Composable
fun DrinkCard(recipe: Recipe, onRecipeSelected: (Recipe) -> Unit) {
    Card(...) {
        Column(...) {
            AsyncImage(
                // ...
                modifier = Modifier
                    // 👇 修改这里的 height 来改变图片的高度
                    .height(120.dp) 
                    .fillMaxWidth()
            )
            Text(
                // 👇 recipe.name 是果汁的名字
                text = recipe.name.replace(" ", "\n"),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                // 👇 修改 style 可以改变文字样式，例如字体、大小等
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                minLines = 2
            )
        }
    }
}
```

## 3. 修改管理员界面 (`AdminScreen.kt`)

打开 `AdminScreen.kt` 文件。

### 修改标题和图标

```kotlin
// 在 AdminScreen.kt 文件中找到 AdminScreen 函数

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(...) {
    Scaffold(
        topBar = {
            TopAppBar(
                // 👇 修改这里的 Text 来改变标题
                title = { Text("后台管理") }, 
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        // 👇 这是返回图标，你可以从 Icons 里面选择其他图标
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCleanClick) {
                        // 👇 这是一键清洗图标
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "一键清洗")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                // 👇 这是添加新配方的 "加号" 图标
                Icon(Icons.Filled.Add, contentDescription = "添加新配方")
            }
        }
    ) { ... }
}
```

### 修改配方列表项

找到 `RecipeAdminCard` 函数，你可以修改列表中每个配方的样式。

```kotlin
// 在 AdminScreen.kt 文件中找到 RecipeAdminCard 函数

@Composable
fun RecipeAdminCard(...) {
    Card(...) {
        Row(...) {
            // 👇 这里显示配方的名字
            Text(text = recipe.name, style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = onEditClick) {
                    // 👇 编辑图标
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDeleteClick) {
                    // 👇 删除图标，可以通过 tint 修改颜色
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
```

## 4. 全局样式修改

### 修改全局颜色

打开 `app/src/main/java/com/example/juicemachine/ui/theme/Color.kt`。

这个文件定义了应用的主题颜色。例如，`primary` 是主色调，通常用于按钮、标题等重要元素。

```kotlin
// 在 Color.kt 文件中
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// ...

// 👇 修改这些颜色值会改变整个应用的日间模式主题颜色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4), // 主要颜色
    secondary = Color(0xFF625b71), // 次要颜色
    background = Color(0xFFFFFBFE), // 背景颜色
    // ... 其他颜色定义
)
```

当你修改了这里的颜色，整个应用中使用了 `MaterialTheme.colorScheme.primary` 的地方都会自动更新。

### 修改全局字体

打开 `app/src/main/java/com/example/juicemachine/ui/theme/Type.kt`。

这个文件定义了应用中使用的不同文本样式，比如标题、正文等。

```kotlin
// 在 Type.kt 文件中

val Typography = Typography(
    bodyLarge = TextStyle( // "bodyLarge" 样式
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, // 字体大小
        lineHeight = 24.sp, //行高
        letterSpacing = 0.5.sp //字间距
    ),
    titleLarge = TextStyle( // "titleLarge" 样式，用于标题
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // ... 其他字体样式
)
```

当你修改了这里的 `fontSize` 或 `fontWeight`，应用中所有使用 `MaterialTheme.typography.bodyLarge` 的文本都会自动更新。

希望这份指南能帮助你轻松地自定义你的果汁机App界面！ 
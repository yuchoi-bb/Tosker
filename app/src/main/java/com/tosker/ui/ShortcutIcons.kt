package com.tosker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 단축버튼에 쓸 수 있는 Material 아이콘 모음.
 * 키(문자열)로 저장하고 키로 다시 아이콘을 찾는다.
 * material-icons-extended는 런타임에 전체 목록을 못 뽑으므로 쓸 만한 것만 골라 둔다.
 */
object ShortcutIcons {

    /** 알파벳 항목의 키 접두사. 예: "alpha:A" */
    const val LETTER_PREFIX = "alpha:"

    /** [icon]이 null이면 아이콘 대신 [key]가 가리키는 글자를 그린다(알파벳 항목). */
    data class Entry(val key: String, val icon: ImageVector?)

    /** 분류별 아이콘. 선택 그리드에서 머리글과 함께 보여준다. */
    val groups: List<Pair<String, List<Entry>>> = listOf(
        "건강·운동" to listOf(
            Entry("medication", Icons.Filled.Medication),
            Entry("water", Icons.Filled.WaterDrop),
            Entry("drink", Icons.Filled.LocalDrink),
            Entry("fitness", Icons.Filled.FitnessCenter),
            Entry("run", Icons.Filled.DirectionsRun),
            Entry("walk", Icons.Filled.DirectionsWalk),
            Entry("bike", Icons.Filled.DirectionsBike),
            Entry("pool", Icons.Filled.Pool),
            Entry("meditation", Icons.Filled.SelfImprovement),
            Entry("heart", Icons.Filled.MonitorHeart),
            Entry("favorite", Icons.Filled.Favorite),
            Entry("spa", Icons.Filled.Spa),
            Entry("bedtime", Icons.Filled.Bedtime),
            Entry("hotel", Icons.Filled.Hotel)
        ),
        "음식" to listOf(
            Entry("restaurant", Icons.Filled.Restaurant),
            Entry("dining", Icons.Filled.LocalDining),
            Entry("fastfood", Icons.Filled.Fastfood),
            Entry("lunch", Icons.Filled.LunchDining),
            Entry("dinner", Icons.Filled.DinnerDining),
            Entry("ramen", Icons.Filled.RamenDining),
            Entry("ricebowl", Icons.Filled.RiceBowl),
            Entry("setmeal", Icons.Filled.SetMeal),
            Entry("bakery", Icons.Filled.BakeryDining),
            Entry("pizza", Icons.Filled.LocalPizza),
            Entry("egg", Icons.Filled.Egg),
            Entry("cookie", Icons.Filled.Cookie),
            Entry("icecream", Icons.Filled.Icecream),
            Entry("cake", Icons.Filled.Cake),
            Entry("breakfast", Icons.Filled.FreeBreakfast),
            Entry("cafe", Icons.Filled.LocalCafe),
            Entry("tea", Icons.Filled.EmojiFoodBeverage),
            Entry("kitchen", Icons.Filled.Kitchen)
        ),
        "술·음료" to listOf(
            Entry("liquor", Icons.Filled.Liquor),
            Entry("wine", Icons.Filled.WineBar),
            Entry("beer", Icons.Filled.SportsBar),
            Entry("cocktail", Icons.Filled.LocalBar),
            Entry("nightlife", Icons.Filled.Nightlife),
            Entry("nodrinks", Icons.Filled.NoDrinks)
        ),
        "업무·공부" to listOf(
            Entry("work", Icons.Filled.Work),
            Entry("email", Icons.Filled.Email),
            Entry("call", Icons.Filled.Call),
            Entry("computer", Icons.Filled.Computer),
            Entry("laptop", Icons.Filled.Laptop),
            Entry("edit", Icons.Filled.Edit),
            Entry("description", Icons.Filled.Description),
            Entry("assignment", Icons.Filled.Assignment),
            Entry("book", Icons.Filled.MenuBook),
            Entry("school", Icons.Filled.School),
            Entry("calculate", Icons.Filled.Calculate),
            Entry("business", Icons.Filled.Business)
        ),
        "돈" to listOf(
            Entry("money", Icons.Filled.AttachMoney),
            Entry("card", Icons.Filled.CreditCard),
            Entry("savings", Icons.Filled.Savings),
            Entry("receipt", Icons.Filled.Receipt),
            Entry("wallet", Icons.Filled.AccountBalanceWallet)
        ),
        "집안일" to listOf(
            Entry("home", Icons.Filled.Home),
            Entry("cleaning", Icons.Filled.CleaningServices),
            Entry("laundry", Icons.Filled.LocalLaundryService),
            Entry("cart", Icons.Filled.ShoppingCart),
            Entry("bag", Icons.Filled.ShoppingBag),
            Entry("store", Icons.Filled.LocalGroceryStore),
            Entry("pets", Icons.Filled.Pets),
            Entry("build", Icons.Filled.Build),
            Entry("delete", Icons.Filled.Delete)
        ),
        "이동" to listOf(
            Entry("car", Icons.Filled.DirectionsCar),
            Entry("gas", Icons.Filled.LocalGasStation),
            Entry("train", Icons.Filled.Train),
            Entry("flight", Icons.Filled.Flight),
            Entry("map", Icons.Filled.Map),
            Entry("place", Icons.Filled.Place)
        ),
        "기타" to listOf(
            Entry("star", Icons.Filled.Star),
            Entry("flag", Icons.Filled.Flag),
            Entry("check", Icons.Filled.CheckCircle),
            Entry("alarm", Icons.Filled.Alarm),
            Entry("schedule", Icons.Filled.Schedule),
            Entry("today", Icons.Filled.Today),
            Entry("event", Icons.Filled.Event),
            Entry("repeat", Icons.Filled.Repeat),
            Entry("notifications", Icons.Filled.Notifications),
            Entry("camera", Icons.Filled.PhotoCamera),
            Entry("music", Icons.Filled.MusicNote),
            Entry("headphones", Icons.Filled.Headphones),
            Entry("tv", Icons.Filled.Tv),
            Entry("game", Icons.Filled.SportsEsports),
            Entry("person", Icons.Filled.Person)
        ),
        "알파벳" to ('A'..'Z').map { Entry("$LETTER_PREFIX$it", null) }
    )

    private val byKey: Map<String, ImageVector> =
        groups.flatMap { it.second }
            .mapNotNull { entry -> entry.icon?.let { entry.key to it } }
            .toMap()

    /** 저장된 키로 아이콘을 찾는다. 없으면 기본 체크 아이콘. */
    fun icon(key: String): ImageVector =
        byKey[key] ?: Icons.Filled.CheckCircle

    /** 알파벳 항목이면 그릴 글자를, 아니면 null을 반환한다. */
    fun letterOf(key: String): String? =
        if (key.startsWith(LETTER_PREFIX)) key.removePrefix(LETTER_PREFIX).take(1) else null

    val defaultKey: String = "check"
}

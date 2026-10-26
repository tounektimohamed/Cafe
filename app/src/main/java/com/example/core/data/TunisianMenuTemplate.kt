package com.example.core.data

import com.example.core.data.entity.*
import java.util.UUID

object TunisianMenuTemplate {

    val categories = listOf(
        CategoryEntity(id = "cat_coffee", nameAr = "القهوة والمشروبات الساخنة", nameFr = "Cafés & Boissons Chaudes", iconName = "local_cafe", sortOrder = 1),
        CategoryEntity(id = "cat_drinks", nameAr = "العصائر والمشروبات الباردة", nameFr = "Jus & Boissons Fraîches", iconName = "local_bar", sortOrder = 2),
        CategoryEntity(id = "cat_pastry", nameAr = "الحلويات والمخبوزات", nameFr = "Pâtisseries & En-cas", iconName = "bakery_dining", sortOrder = 3),
        CategoryEntity(id = "cat_shisha", nameAr = "الشيشة والمعسل", nameFr = "Chicha & Narguilé", iconName = "smoking_rooms", sortOrder = 4)
    )

    val products = listOf(
        // Coffee
        ProductEntity(id = "prod_express", categoryId = "cat_coffee", nameAr = "إكسبريس (Café Express)", nameFr = "Café Express", priceMillimes = 1400L, isFavorite = true, sortOrder = 1),
        ProductEntity(id = "prod_capucin", categoryId = "cat_coffee", nameAr = "كابوسان (Capucin)", nameFr = "Capucin", priceMillimes = 1600L, isFavorite = true, sortOrder = 2),
        ProductEntity(id = "prod_direct", categoryId = "cat_coffee", nameAr = "ديريكت (Direct)", nameFr = "Café au lait (Direct)", priceMillimes = 2000L, isFavorite = true, sortOrder = 3),
        ProductEntity(id = "prod_creme", categoryId = "cat_coffee", nameAr = "كافي كريم (Café Crème)", nameFr = "Café Crème", priceMillimes = 2200L, isFavorite = false, sortOrder = 4),
        ProductEntity(id = "prod_tea_red", categoryId = "cat_coffee", nameAr = "تاي أحمر بالنعناع (Thé Rouge)", nameFr = "Thé Rouge Menthe", priceMillimes = 1200L, isFavorite = true, sortOrder = 5),
        ProductEntity(id = "prod_tea_pignons", categoryId = "cat_coffee", nameAr = "تاي بالبندق / مكسرات (Thé Pignons)", nameFr = "Thé aux Pignons", priceMillimes = 3500L, isFavorite = true, sortOrder = 6),
        ProductEntity(id = "prod_turc", categoryId = "cat_coffee", nameAr = "قهوة عربي بالمزهر (Café Arabe)", nameFr = "Café Turc / Arabe", priceMillimes = 2500L, isFavorite = false, sortOrder = 7),

        // Fresh drinks
        ProductEntity(id = "prod_citronnade", categoryId = "cat_drinks", nameAr = "سيتروناد تونسية طبيعية", nameFr = "Citronnade Tunisienne", priceMillimes = 2800L, isFavorite = true, sortOrder = 8),
        ProductEntity(id = "prod_orange", categoryId = "cat_drinks", nameAr = "عصير برتقال طازج", nameFr = "Jus d'Orange Frais", priceMillimes = 3200L, isFavorite = true, sortOrder = 9),
        ProductEntity(id = "prod_cocktail", categoryId = "cat_drinks", nameAr = "عصير كوكتيل غلال", nameFr = "Cocktail de Fruits", priceMillimes = 4500L, isFavorite = false, sortOrder = 10),
        ProductEntity(id = "prod_water_small", categoryId = "cat_drinks", nameAr = "ماء معدني 0.5 لتر", nameFr = "Eau Minérale 0.5L", priceMillimes = 1000L, isFavorite = true, sortOrder = 11),
        ProductEntity(id = "prod_soda", categoryId = "cat_drinks", nameAr = "مشروب غازي بوڨا / كوكا", nameFr = "Soda Boga / Coca", priceMillimes = 2000L, isFavorite = true, sortOrder = 12),

        // Pastries
        ProductEntity(id = "prod_croissant", categoryId = "cat_pastry", nameAr = "كرواسون بالزبدة", nameFr = "Croissant Beurre", priceMillimes = 1500L, isFavorite = true, sortOrder = 13),
        ProductEntity(id = "prod_millefeuille", categoryId = "cat_pastry", nameAr = "ميلفاي تقليدي", nameFr = "Mille-feuille", priceMillimes = 2000L, isFavorite = true, sortOrder = 14),
        ProductEntity(id = "prod_cake", categoryId = "cat_pastry", nameAr = "كعكة شكلاطة فاخرة", nameFr = "Gâteau Chocolat", priceMillimes = 2500L, isFavorite = false, sortOrder = 15),
        ProductEntity(id = "prod_crepe", categoryId = "cat_pastry", nameAr = "كراب نوتيلا وسورغو", nameFr = "Crêpe Nutella", priceMillimes = 4500L, isFavorite = false, sortOrder = 16),

        // Shisha
        ProductEntity(id = "prod_shisha_apple", categoryId = "cat_shisha", nameAr = "شيشة تفاحتين فاخر", nameFr = "Chicha Double Pomme", priceMillimes = 6000L, isFavorite = true, sortOrder = 17),
        ProductEntity(id = "prod_shisha_mint", categoryId = "cat_shisha", nameAr = "شيشة نعناع بارد", nameFr = "Chicha Menthe", priceMillimes = 6000L, isFavorite = false, sortOrder = 18),
        ProductEntity(id = "prod_shisha_grape", categoryId = "cat_shisha", nameAr = "شيشة عنب وتوت", nameFr = "Chicha Raisin", priceMillimes = 6500L, isFavorite = false, sortOrder = 19)
    )

    val modifiers = listOf(
        ModifierOptionEntity(id = "mod_no_sugar", nameAr = "بدون سكر", extraPriceMillimes = 0L, isSugar = true),
        ModifierOptionEntity(id = "mod_extra_sugar", nameAr = "سكر زيادة", extraPriceMillimes = 0L, isSugar = true),
        ModifierOptionEntity(id = "mod_light_sugar", nameAr = "سكر خفيف", extraPriceMillimes = 0L, isSugar = true),
        ModifierOptionEntity(id = "mod_extra_milk", nameAr = "زيادة حليب", extraPriceMillimes = 300L, isSugar = false),
        ModifierOptionEntity(id = "mod_extra_nuts", nameAr = "إضافة بندق / لوز", extraPriceMillimes = 1500L, isSugar = false)
    )

    fun createInitialTables(count: Int = 12): List<DiningTableEntity> {
        val tables = mutableListOf<DiningTableEntity>()
        // Takeaway table
        tables.add(
            DiningTableEntity(
                id = "table_takeaway",
                tableNumber = 0,
                name = "طلبية سريعة (سفري)",
                capacity = 1,
                status = "FREE",
                isTakeaway = true
            )
        )
        for (i in 1..count) {
            tables.add(
                DiningTableEntity(
                    id = "table_$i",
                    tableNumber = i,
                    name = "طاولة $i",
                    capacity = 4,
                    status = "FREE",
                    isTakeaway = false
                )
            )
        }
        return tables
    }

    fun createDefaultStaff(): List<StaffEntity> {
        return listOf(
            StaffEntity(
                id = "staff_manager",
                name = "المدير (Gérant)",
                role = "MANAGER",
                pinHash = "1234", // Default PIN: 1234
                active = true
            ),
            StaffEntity(
                id = "staff_counter",
                name = "المحاسب محمد",
                role = "COUNTER",
                pinHash = "0000", // Default PIN: 0000
                active = true
            ),
            StaffEntity(
                id = "staff_waiter1",
                name = "النادل أنيس",
                role = "WAITER",
                pinHash = "1111", // Default PIN: 1111
                active = true
            ),
            StaffEntity(
                id = "staff_waiter2",
                name = "النادل سامي",
                role = "WAITER",
                pinHash = "2222", // Default PIN: 2222
                active = true
            ),
            StaffEntity(
                id = "staff_kitchen",
                name = "المطبخ والبار",
                role = "KITCHEN",
                pinHash = "3333", // Default PIN: 3333
                active = true
            )
        )
    }
}

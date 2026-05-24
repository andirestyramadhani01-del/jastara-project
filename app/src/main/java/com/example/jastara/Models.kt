package com.example.jastara

import java.io.Serializable

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = ""
) : Serializable

data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val tripId: String = "",
    val stock: Int = 0,
    val description: String = "",
    val imageType: String = "" // "bag", "skincare", "perfume"
) : Serializable

data class Trip(
    val id: String = "",
    val destination: String = "",
    val dates: String = "",
    val imageType: String = "" // "korea", "japan", "singapore"
) : Serializable

data class Order(
    val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val productPrice: Double = 0.0,
    val qty: Int = 1,
    val address: String = "",
    val notes: String = "",
    val shippingFee: Double = 20000.0,
    val grandTotal: Double = 0.0,
    val paymentMethod: String = "", // "COD", "FULL", "DP50"
    var status: String = "Menunggu", // "Menunggu", "Diproses", "Dikirim", "Selesai"
    val paymentProofUrl: String? = null,
    var balanceProofUrl: String? = null,
    var isBalancePaid: Boolean = false
) : Serializable

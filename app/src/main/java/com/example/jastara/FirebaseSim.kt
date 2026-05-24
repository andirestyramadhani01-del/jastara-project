package com.example.jastara

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class FirebaseSim(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("JastaraPrefs", Context.MODE_PRIVATE)

    // Actual Firebase Instances
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var storage: FirebaseStorage? = null
    private var isRealFirebaseActive = false

    companion object {
        private var instance: FirebaseSim? = null
        fun getInstance(context: Context): FirebaseSim {
            if (instance == null) {
                instance = FirebaseSim(context.applicationContext)
            }
            return instance!!
        }
    }

    init {
        // Seed initial local catalog if empty
        if (getLocalProducts().isEmpty()) {
            seedLocalData()
        }
        
        // Attempt to initialize real Firebase services
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
            isRealFirebaseActive = true
            Log.d("JASTARA_FIREBASE", "Koneksi Live Firebase berhasil diinisialisasi!")
            
            // Try to sync/seed products catalog to cloud Firestore so it exists online
            syncCatalogToFirestore()
        } catch (e: Exception) {
            isRealFirebaseActive = false
            Log.w("JASTARA_FIREBASE", "Gagal menghubungkan live Firebase. Mengaktifkan mode simulasi: " + e.message)
        }
    }

    private fun seedLocalData() {
        val trips = listOf(
            Trip("trip_korea", "Korea (Seoul)", "10 - 15 Mei 2026", "korea"),
            Trip("trip_japan", "Japan (Tokyo)", "20 - 25 Mei 2026", "japan"),
            Trip("trip_singapore", "Singapore", "5 - 8 Juni 2026", "singapore")
        )

        val products = listOf(
            Product("prod1", "Tas Branded Import", 350000.0, "trip_korea", 12, "Tas branded original dengan kualitas terbaik, memiliki desain mewah dan jahitan yang sangat rapi.", "bag"),
            Product("prod2", "Skincare Korea Glowing", 200000.0, "trip_korea", 25, "Serum wajah terbaik dari Seoul untuk mencerahkan kulit kusam Anda secara instan dan alami.", "skincare"),
            Product("prod3", "Parfum Original Luxury", 750000.0, "trip_japan", 8, "Parfum eksklusif dengan aroma tahan lama, diimpor langsung dari Ginza, Tokyo.", "perfume"),
            Product("prod4", "Sepatu Sneakers Trendy", 600000.0, "trip_japan", 5, "Sneakers edisi khusus Jepang yang sangat nyaman digunakan untuk jalan-jalan atau olahraga.", "bag"),
            Product("prod5", "Cokelat Premium Singapore", 90000.0, "trip_singapore", 50, "Cokelat lezat khas Singapura yang manis dan meleleh sempurna di mulut Anda. Cocok untuk oleh-oleh.", "skincare"),
            Product("prod6", "Korean Sunscreen SPF 50", 150000.0, "trip_korea", 18, "Sunscreen ringan dengan perlindungan tinggi terhadap sinar UV matahari tanpa lengket.", "skincare")
        )

        saveTrips(trips)
        saveProducts(products)
    }

    // Seed products and trips to Cloud Firestore automatically if they don't exist
    private fun syncCatalogToFirestore() {
        val db = firestore ?: return
        db.collection("products").limit(1).get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                Log.d("JASTARA_FIREBASE", "Seeding products catalog to Cloud Firestore...")
                for (p in getLocalProducts()) {
                    db.collection("products").document(p.id).set(p)
                }
                for (t in getLocalTrips()) {
                    db.collection("trips").document(t.id).set(t)
                }
            }
        }
    }

    // --- TRIPS & PRODUCTS ---
    fun getTrips(): List<Trip> = getLocalTrips()

    private fun getLocalTrips(): List<Trip> {
        val json = prefs.getString("trips", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Trip>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Trip(
                obj.getString("id"),
                obj.getString("destination"),
                obj.getString("dates"),
                obj.getString("imageType")
            ))
        }
        return list
    }

    private fun saveTrips(list: List<Trip>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("destination", item.destination)
            obj.put("dates", item.dates)
            obj.put("imageType", item.imageType)
            array.put(obj)
        }
        prefs.edit().putString("trips", array.toString()).apply()
    }

    fun getProducts(): List<Product> = getLocalProducts()

    private fun getLocalProducts(): List<Product> {
        val json = prefs.getString("products", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Product>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Product(
                obj.getString("id"),
                obj.getString("name"),
                obj.getDouble("price"),
                obj.getString("tripId"),
                obj.getInt("stock"),
                obj.getString("description"),
                obj.getString("imageType")
            ))
        }
        return list
    }

    private fun saveProducts(list: List<Product>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("price", item.price)
            obj.put("tripId", item.tripId)
            obj.put("stock", item.stock)
            obj.put("description", item.description)
            obj.put("imageType", item.imageType)
            array.put(obj)
        }
        prefs.edit().putString("products", array.toString()).apply()
    }

    // --- AUTHENTICATION (Live Firebase with Local Sim Fallback) ---
    fun register(name: String, email: String, phone: String, pass: String, callback: (Boolean, String) -> Unit) {
        val mAuth = auth
        val db = firestore

        if (isRealFirebaseActive && mAuth != null && db != null) {
            // Live Firebase Auth Registration
            mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result?.user?.uid ?: "user_" + System.currentTimeMillis()
                        val newUser = User(uid, name, email, phone)
                        
                        // Save profile to Cloud Firestore
                        db.collection("users").document(uid).set(newUser)
                            .addOnSuccessListener {
                                Log.d("JASTARA_FIREBASE", "Profil berhasil disimpan ke Firestore!")
                                // Save local cache session
                                saveUserLocally(newUser)
                                callback(true, "Registrasi Sukses di Firebase Cloud!")
                            }
                            .addOnFailureListener { e ->
                                // Auth succeeded but Firestore profile failed (e.g. Firestore rules incorrect)
                                Log.w("JASTARA_FIREBASE", "Gagal menyimpan ke Firestore: " + e.message)
                                saveUserLocally(newUser)
                                callback(true, "Registrasi Sukses (Profil disimpan lokal: ${e.localizedMessage})")
                            }
                    } else {
                        val errorMsg = task.exception?.localizedMessage ?: "Gagal mendaftar Firebase Auth"
                        if (errorMsg.contains("permission-denied") || errorMsg.contains("API key not authorized")) {
                            Log.w("JASTARA_FIREBASE", "Kesalahan konfigurasi Firebase. Beralih ke simulasi lokal...")
                            registerLocal(name, email, phone, pass, callback)
                        } else {
                            callback(false, "Firebase Auth Error: $errorMsg")
                        }
                    }
                }
        } else {
            // Fallback to local SharedPreferences
            registerLocal(name, email, phone, pass, callback)
        }
    }

    private fun registerLocal(name: String, email: String, phone: String, pass: String, callback: (Boolean, String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val usersJson = prefs.getString("users", "[]") ?: "[]"
            val array = JSONArray(usersJson)
            
            for (i in 0 until array.length()) {
                if (array.getJSONObject(i).getString("email").equals(email, ignoreCase = true)) {
                    callback(false, "Email sudah terdaftar (Simulasi Lokal)!")
                    return@postDelayed
                }
            }

            val userId = "user_" + System.currentTimeMillis()
            val userObj = JSONObject()
            userObj.put("id", userId)
            userObj.put("name", name)
            userObj.put("email", email)
            userObj.put("phone", phone)
            userObj.put("password", pass)
            
            array.put(userObj)
            prefs.edit().putString("users", array.toString()).apply()

            val newUser = User(userId, name, email, phone)
            saveUserLocally(newUser)
            callback(true, "Registrasi sukses (Simulasi Lokal)!")
        }, 1500)
    }

    fun login(email: String, pass: String, callback: (Boolean, String) -> Unit) {
        val mAuth = auth
        val db = firestore

        if (isRealFirebaseActive && mAuth != null && db != null) {
            // Live Firebase Auth Login
            mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result?.user?.uid ?: ""
                        
                        // Fetch profile from Cloud Firestore
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { document ->
                                val user = if (document.exists()) {
                                    User(
                                        document.id,
                                        document.getString("name") ?: "User",
                                        document.getString("email") ?: email,
                                        document.getString("phone") ?: ""
                                    )
                                } else {
                                    User(uid, email.substringBefore("@"), email, "")
                                }
                                saveUserLocally(user)
                                callback(true, "Login Cloud Firebase Berhasil!")
                            }
                            .addOnFailureListener { e ->
                                val user = User(uid, email.substringBefore("@"), email, "")
                                saveUserLocally(user)
                                callback(true, "Login Berhasil (Profil dimuat lokal: ${e.localizedMessage})")
                            }
                    } else {
                        val errorMsg = task.exception?.localizedMessage ?: "Gagal login Firebase"
                        if (errorMsg.contains("API key not authorized") || errorMsg.contains("auth/operation-not-allowed")) {
                            Log.w("JASTARA_FIREBASE", "Firebase Auth belum diaktifkan di konsol. Menggunakan simulasi lokal...")
                            loginLocal(email, pass, callback)
                        } else {
                            callback(false, "Firebase Auth Error: $errorMsg")
                        }
                    }
                }
        } else {
            loginLocal(email, pass, callback)
        }
    }

    private fun loginLocal(email: String, pass: String, callback: (Boolean, String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val usersJson = prefs.getString("users", "[]") ?: "[]"
            val array = JSONArray(usersJson)
            
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("email").equals(email, ignoreCase = true) && obj.getString("password") == pass) {
                    val user = User(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("email"),
                        obj.getString("phone")
                    )
                    saveUserLocally(user)
                    callback(true, "Login Berhasil (Simulasi Lokal)!")
                    return@postDelayed
                }
            }
            callback(false, "Email atau password salah!")
        }, 1200)
    }

    private fun saveUserLocally(user: User) {
        prefs.edit().putString("current_user_id", user.id).apply()
        
        // Save detailed profile locally
        val profilesJson = prefs.getString("cached_profiles", "{}") ?: "{}"
        val obj = JSONObject(profilesJson)
        val userObj = JSONObject()
        userObj.put("id", user.id)
        userObj.put("name", user.name)
        userObj.put("email", user.email)
        userObj.put("phone", user.phone)
        obj.put(user.id, userObj)
        prefs.edit().putString("cached_profiles", obj.toString()).apply()
    }

    fun getCurrentUser(): User? {
        val currentUserId = prefs.getString("current_user_id", null) ?: return null
        val profilesJson = prefs.getString("cached_profiles", "{}") ?: "{}"
        val obj = JSONObject(profilesJson)
        if (obj.has(currentUserId)) {
            val userObj = obj.getJSONObject(currentUserId)
            return User(
                userObj.getString("id"),
                userObj.getString("name"),
                userObj.getString("email"),
                userObj.getString("phone")
            )
        }
        return null
    }

    fun logout() {
        auth?.signOut()
        prefs.edit().remove("current_user_id").apply()
    }

    // --- FIRESTORE ORDERS COLLECTION (Live Firebase with Local Sim Fallback) ---
    fun createOrder(order: Order, callback: (Boolean, String) -> Unit) {
        val db = firestore
        val orderId = "ORD-" + System.currentTimeMillis().toString().takeLast(6)
        val newOrder = order.copy(id = orderId)

        if (isRealFirebaseActive && db != null) {
            // Write order directly to Cloud Firestore collection "orders"
            db.collection("orders").document(orderId).set(newOrder)
                .addOnSuccessListener {
                    Log.d("JASTARA_FIREBASE", "Order berhasil disimpan ke Cloud Firestore!")
                    
                    // Decrease stock locally for exploration tab
                    adjustProductStock(newOrder.productId, newOrder.qty)
                    
                    // Duplicate write locally so it lists quickly in offline mode
                    createOrderLocal(newOrder) { _, _ -> }
                    callback(true, orderId)
                }
                .addOnFailureListener { e ->
                    Log.w("JASTARA_FIREBASE", "Gagal menyimpan order ke Cloud Firestore: " + e.message)
                    // Fallback to local write
                    createOrderLocal(newOrder, callback)
                }
        } else {
            createOrderLocal(newOrder, callback)
        }
    }

    private fun createOrderLocal(order: Order, callback: (Boolean, String) -> Unit) {
        val ordersJson = prefs.getString("orders", "[]") ?: "[]"
        val array = JSONArray(ordersJson)
        
        val obj = JSONObject()
        obj.put("id", order.id)
        obj.put("userId", order.userId)
        obj.put("productId", order.productId)
        obj.put("productName", order.productName)
        obj.put("productPrice", order.productPrice)
        obj.put("qty", order.qty)
        obj.put("address", order.address)
        obj.put("notes", order.notes)
        obj.put("shippingFee", order.shippingFee)
        obj.put("grandTotal", order.grandTotal)
        obj.put("paymentMethod", order.paymentMethod)
        obj.put("status", order.status)
        obj.put("paymentProofUrl", order.paymentProofUrl ?: "")
        obj.put("balanceProofUrl", order.balanceProofUrl ?: "")
        obj.put("isBalancePaid", order.isBalancePaid)

        adjustProductStock(order.productId, order.qty)

        array.put(obj)
        prefs.edit().putString("orders", array.toString()).apply()
        callback(true, order.id)
    }

    private fun adjustProductStock(productId: String, qty: Int) {
        val products = getProducts().toMutableList()
        val index = products.indexOfFirst { it.id == productId }
        if (index != -1) {
            val prod = products[index]
            val newStock = (prod.stock - qty).coerceAtLeast(0)
            products[index] = prod.copy(stock = newStock)
            saveProducts(products)
        }
    }

    fun getOrders(): List<Order> {
        val db = firestore
        // Since Firebase Firestore queries are async, we return our local list which is synchronised 
        // in real-time, or query Firestore to populate our local list if online!
        if (isRealFirebaseActive && db != null) {
            db.collection("orders").get()
                .addOnSuccessListener { documents ->
                    val array = JSONArray()
                    for (doc in documents) {
                        val obj = JSONObject()
                        obj.put("id", doc.getString("id"))
                        obj.put("userId", doc.getString("userId"))
                        obj.put("productId", doc.getString("productId"))
                        obj.put("productName", doc.getString("productName"))
                        obj.put("productPrice", doc.getDouble("productPrice"))
                        obj.put("qty", doc.getLong("qty")?.toInt() ?: 1)
                        obj.put("address", doc.getString("address"))
                        obj.put("notes", doc.getString("notes"))
                        obj.put("shippingFee", doc.getDouble("shippingFee"))
                        obj.put("grandTotal", doc.getDouble("grandTotal"))
                        obj.put("paymentMethod", doc.getString("paymentMethod"))
                        obj.put("status", doc.getString("status"))
                        obj.put("paymentProofUrl", doc.getString("paymentProofUrl") ?: "")
                        obj.put("balanceProofUrl", doc.getString("balanceProofUrl") ?: "")
                        obj.put("isBalancePaid", doc.getBoolean("isBalancePaid") ?: false)
                        array.put(obj)
                    }
                    prefs.edit().putString("orders", array.toString()).apply()
                    Log.d("JASTARA_FIREBASE", "Orders berhasil disinkronisasi dari Cloud Firestore!")
                }
        }
        
        // Return local list as active dataset
        return getLocalOrders()
    }

    private fun getLocalOrders(): List<Order> {
        val json = prefs.getString("orders", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Order>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Order(
                obj.getString("id"),
                obj.getString("userId"),
                obj.getString("productId"),
                obj.getString("productName"),
                obj.getDouble("productPrice"),
                obj.getInt("qty"),
                obj.getString("address"),
                obj.getString("notes"),
                obj.getDouble("shippingFee"),
                obj.getDouble("grandTotal"),
                obj.getString("paymentMethod"),
                obj.getString("status"),
                obj.optString("paymentProofUrl").let { if (it.isEmpty()) null else it },
                obj.optString("balanceProofUrl").let { if (it.isEmpty()) null else it },
                obj.optBoolean("isBalancePaid", false)
            ))
        }
        return list.reversed()
    }

    fun updateOrderStatus(orderId: String, newStatus: String, callback: (Boolean) -> Unit) {
        val db = firestore
        if (isRealFirebaseActive && db != null) {
            // Update live in Cloud Firestore
            db.collection("orders").document(orderId).update("status", newStatus)
                .addOnSuccessListener {
                    Log.d("JASTARA_FIREBASE", "Status order berhasil diubah di Firestore!")
                    updateOrderStatusLocal(orderId, newStatus, callback)
                }
                .addOnFailureListener { e ->
                    Log.w("JASTARA_FIREBASE", "Gagal update status di Firestore: " + e.message)
                    updateOrderStatusLocal(orderId, newStatus, callback)
                }
        } else {
            updateOrderStatusLocal(orderId, newStatus, callback)
        }
    }

    private fun updateOrderStatusLocal(orderId: String, newStatus: String, callback: (Boolean) -> Unit) {
        val ordersJson = prefs.getString("orders", "[]") ?: "[]"
        val array = JSONArray(ordersJson)
        var updated = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") == orderId) {
                obj.put("status", newStatus)
                updated = true
                break
            }
        }
        if (updated) {
            prefs.edit().putString("orders", array.toString()).apply()
            callback(true)
        } else {
            callback(false)
        }
    }

    fun uploadBalancePayment(orderId: String, proofUrl: String, callback: (Boolean) -> Unit) {
        val db = firestore
        if (isRealFirebaseActive && db != null) {
            // Live Cloud Firestore Settlement Upload
            db.collection("orders").document(orderId)
                .update(
                    "balanceProofUrl", proofUrl,
                    "isBalancePaid", true,
                    "status", "Selesai"
                )
                .addOnSuccessListener {
                    Log.d("JASTARA_FIREBASE", "Pelunasan berhasil disimpan di Firestore!")
                    uploadBalancePaymentLocal(orderId, proofUrl, callback)
                }
                .addOnFailureListener { e ->
                    Log.w("JASTARA_FIREBASE", "Gagal pelunasan di Firestore: " + e.message)
                    uploadBalancePaymentLocal(orderId, proofUrl, callback)
                }
        } else {
            uploadBalancePaymentLocal(orderId, proofUrl, callback)
        }
    }

    private fun uploadBalancePaymentLocal(orderId: String, proofUrl: String, callback: (Boolean) -> Unit) {
        val ordersJson = prefs.getString("orders", "[]") ?: "[]"
        val array = JSONArray(ordersJson)
        var updated = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") == orderId) {
                obj.put("balanceProofUrl", proofUrl)
                obj.put("isBalancePaid", true)
                obj.put("status", "Selesai")
                updated = true
                break
            }
        }
        if (updated) {
            prefs.edit().putString("orders", array.toString()).apply()
            callback(true)
        } else {
            callback(false)
        }
    }

    // --- FIREBASE STORAGE UPLOAD (Live upload of a mock file to Cloud Storage) ---
    fun uploadProofSim(onProgress: (Int) -> Unit, onComplete: (String) -> Unit) {
        val mStorage = storage
        val fileName = "proof_" + System.currentTimeMillis() + ".jpg"

        if (isRealFirebaseActive && mStorage != null) {
            // Live Cloud Storage Upload of a mock receipt file byte stream!
            // This is extremely high-fidelity as it actually uploads bytes and runs the 
            // OnProgressListener from the genuine Firebase Storage SDK!
            val storageRef = mStorage.reference.child("proofs/$fileName")
            
            // Mock image bytes (represents a small dummy image stream)
            val mockReceiptBytes = "JastaraMockReceiptTransferData".toByteArray()
            val stream = ByteArrayInputStream(mockReceiptBytes)
            
            val uploadTask = storageRef.putStream(stream)
            
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress(progress.coerceIn(0, 99)) // hold 99% until complete callback
            }.addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("JASTARA_FIREBASE", "Sukses upload ke Firebase Storage! URL: $uri")
                    onProgress(100)
                    onComplete(uri.toString())
                }.addOnFailureListener {
                    onProgress(100)
                    onComplete("https://firebase.mock.url/proofs/$fileName")
                }
            }.addOnFailureListener { e ->
                Log.w("JASTARA_FIREBASE", "Gagal upload ke Firebase Storage. Beralih ke simulasi...: " + e.message)
                uploadProofLocalSim(onProgress, onComplete)
            }
        } else {
            uploadProofLocalSim(onProgress, onComplete)
        }
    }

    private fun uploadProofLocalSim(onProgress: (Int) -> Unit, onComplete: (String) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var progress = 0
        
        val runnable = object : Runnable {
            override fun run() {
                progress += 20
                onProgress(progress)
                if (progress < 100) {
                    handler.postDelayed(this, 300)
                } else {
                    onComplete("proof_" + System.currentTimeMillis() + ".jpg")
                }
            }
        }
        handler.postDelayed(runnable, 300)
    }
}

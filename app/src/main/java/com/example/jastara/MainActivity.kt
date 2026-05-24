package com.example.jastara

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseSim: FirebaseSim

    // Global Screen Containers
    private lateinit var splashContainer: View
    private lateinit var loginContainer: View
    private lateinit var registerContainer: View
    private lateinit var homeContainer: View
    private lateinit var detailContainer: View
    private lateinit var checkoutContainer: View
    private lateinit var trackingContainer: View

    // Active Objects during flow
    private var currentUser: User? = null
    private var selectedProduct: Product? = null
    private var currentCheckoutQty = 1
    private var isProofUploaded = false
    private var uploadedProofUrl: String? = null
    private var trackingOrder: Order? = null
    private var activeTabId = R.id.navHome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Adjust system bars layout padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Simulation
        firebaseSim = FirebaseSim.getInstance(this)

        // Bind all global screen layers
        splashContainer = findViewById(R.id.splashContainer)
        loginContainer = findViewById(R.id.loginContainer)
        registerContainer = findViewById(R.id.registerContainer)
        homeContainer = findViewById(R.id.homeContainer)
        detailContainer = findViewById(R.id.detailContainer)
        checkoutContainer = findViewById(R.id.checkoutContainer)
        trackingContainer = findViewById(R.id.trackingContainer)

        // Start flow with Splash Screen
        setupSplash()
        setupLogin()
        setupRegister()
        setupHome()
        setupDetail()
        setupCheckout()
        setupTracking()
    }

    // Swaps visual layer visibility with simple fade-in
    private fun switchView(targetView: View) {
        val views = listOf(
            splashContainer, loginContainer, registerContainer,
            homeContainer, detailContainer, checkoutContainer, trackingContainer
        )
        for (v in views) {
            if (v == targetView) {
                v.visibility = View.VISIBLE
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(300).start()
            } else {
                v.visibility = View.GONE
            }
        }
    }

    // --- PHASE 1: SPLASH SCREEN & AUTO-LOGIN ---
    private fun setupSplash() {
        val splashProgress = findViewById<ProgressBar>(R.id.splashProgress)
        val splashStatusText = findViewById<TextView>(R.id.splashStatusText)

        // Simulate session checking
        Handler(Looper.getMainLooper()).postDelayed({
            currentUser = firebaseSim.getCurrentUser()
            if (currentUser != null) {
                splashStatusText.text = "Sesi ditemukan! Masuk Beranda..."
                Handler(Looper.getMainLooper()).postDelayed({
                    updateWelcomeHeader()
                    switchTab(R.id.navHome)
                    switchView(homeContainer)
                }, 800)
            } else {
                splashStatusText.text = "Belum login. Membuka login..."
                Handler(Looper.getMainLooper()).postDelayed({
                    switchView(loginContainer)
                }, 800)
            }
        }, 1500)
    }

    // --- PHASE 1: LOGIN ---
    private fun setupLogin() {
        val edtEmail = findViewById<EditText>(R.id.loginEmail)
        val edtPassword = findViewById<EditText>(R.id.loginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtGoToRegister = findViewById<TextView>(R.id.txtGoToRegister)

        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val pass = edtPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email dan password wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Memproses..."

            firebaseSim.login(email, pass) { success, msg ->
                btnLogin.isEnabled = true
                btnLogin.text = "Login"
                if (success) {
                    currentUser = firebaseSim.getCurrentUser()
                    updateWelcomeHeader()
                    switchTab(R.id.navHome)
                    switchView(homeContainer)
                    edtEmail.text.clear()
                    edtPassword.text.clear()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        txtGoToRegister.setOnClickListener {
            switchView(registerContainer)
        }
    }

    // --- PHASE 1: REGISTER WITH FIREBASE AUTH SPINNER ---
    private fun setupRegister() {
        val edtName = findViewById<EditText>(R.id.registerName)
        val edtEmail = findViewById<EditText>(R.id.registerEmail)
        val edtPhone = findViewById<EditText>(R.id.registerPhone)
        val edtPassword = findViewById<EditText>(R.id.registerPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val txtGoToLogin = findViewById<TextView>(R.id.txtGoToLogin)

        val overlay = findViewById<View>(R.id.registerValidationOverlay)
        val overlaySubText = findViewById<TextView>(R.id.registerOverlaySubText)
        val overlayProgress = findViewById<ProgressBar>(R.id.registerOverlayProgress)
        val overlaySuccess = findViewById<ImageView>(R.id.registerOverlaySuccessIcon)

        btnRegister.setOnClickListener {
            val name = edtName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val phone = edtPhone.text.toString().trim()
            val pass = edtPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Semua data wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show Firebase validation overlay
            overlay.visibility = View.VISIBLE
            overlaySubText.text = "Memproses data pendaftaran..."
            overlayProgress.visibility = View.VISIBLE
            overlaySuccess.visibility = View.GONE

            firebaseSim.register(name, email, phone, pass) { success, msg ->
                if (success) {
                    overlaySubText.text = "Sukses! Akun Anda berhasil terdaftar."
                    overlayProgress.visibility = View.GONE
                    overlaySuccess.visibility = View.VISIBLE

                    Handler(Looper.getMainLooper()).postDelayed({
                        overlay.visibility = View.GONE
                        currentUser = firebaseSim.getCurrentUser()
                        updateWelcomeHeader()
                        switchTab(R.id.navHome)
                        switchView(homeContainer)

                        // Clear inputs
                        edtName.text.clear()
                        edtEmail.text.clear()
                        edtPhone.text.clear()
                        edtPassword.text.clear()
                    }, 1200)
                } else {
                    overlay.visibility = View.GONE
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        txtGoToLogin.setOnClickListener {
            switchView(loginContainer)
        }
    }

    // --- PHASE 2: EXPLORATION (BERANDA HYBRID) ---
    private fun setupHome() {
        val searchEdit = findViewById<EditText>(R.id.searchEditText)
        val suggestionsCard = findViewById<MaterialCardView>(R.id.searchSuggestionsCard)
        val suggestionsList = findViewById<LinearLayout>(R.id.searchSuggestionsList)

        // Header and tabs binding
        findViewById<TextView>(R.id.txtHomeSeeAllProducts).setOnClickListener {
            switchTab(R.id.navCatalog)
        }

        // Setup bottom navigation bindings
        val navItems = mapOf(
            R.id.navHome to findViewById<LinearLayout>(R.id.navHome),
            R.id.navTrip to findViewById<LinearLayout>(R.id.navTrip),
            R.id.navCatalog to findViewById<LinearLayout>(R.id.navCatalog),
            R.id.navOrders to findViewById<LinearLayout>(R.id.navOrders),
            R.id.navAccount to findViewById<LinearLayout>(R.id.navAccount)
        )

        for ((id, layout) in navItems) {
            layout.setOnClickListener {
                switchTab(id)
            }
        }

        // Quick Category icons bindings
        findViewById<LinearLayout>(R.id.btnCatTrip).setOnClickListener { switchTab(R.id.navTrip) }
        findViewById<LinearLayout>(R.id.btnCatKatalog).setOnClickListener { switchTab(R.id.navCatalog) }
        findViewById<LinearLayout>(R.id.btnCatPromo).setOnClickListener { switchTab(R.id.navCatalog) }
        findViewById<LinearLayout>(R.id.btnCatTerbaru).setOnClickListener { switchTab(R.id.navCatalog) }

        // Setup Profile features
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            firebaseSim.logout()
            currentUser = null
            switchView(loginContainer)
            Toast.makeText(this, "Logout berhasil!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnOpenAdminSim).setOnClickListener {
            // switch to orders tab to let them view tracking and use the admin panel!
            switchTab(R.id.navOrders)
            Toast.makeText(this, "Pilih pesanan Anda di bawah untuk membuka panel admin!", Toast.LENGTH_LONG).show()
        }

        // Search engine binding (Phase 2 Ketik di Search Bar)
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    suggestionsCard.visibility = View.VISIBLE
                    populateSuggestions(query, suggestionsList, suggestionsCard, searchEdit)
                } else {
                    suggestionsCard.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateWelcomeHeader() {
        val welcome = findViewById<TextView>(R.id.txtWelcomeUser)
        val name = currentUser?.name ?: "Pengguna"
        welcome.text = "Hai, Selamat Datang!\n$name"

        // Account tab profile bindings
        findViewById<TextView>(R.id.txtProfileName).text = name
        findViewById<TextView>(R.id.txtProfileEmail).text = currentUser?.email ?: ""
        findViewById<TextView>(R.id.txtProfilePhone).text = currentUser?.phone ?: ""
    }

    // Handles the bottom navigation tabs switching
    private fun switchTab(tabId: Int) {
        activeTabId = tabId

        // Active items
        val tabHome = findViewById<View>(R.id.tabHomeView)
        val tabTrip = findViewById<View>(R.id.tabTripView)
        val tabKatalog = findViewById<View>(R.id.tabKatalogView)
        val tabOrders = findViewById<View>(R.id.tabOrdersView)
        val tabAkun = findViewById<View>(R.id.tabAkunView)

        val headerTitle = findViewById<TextView>(R.id.txtHeaderTitle)

        // Bottom icons
        val tabs = listOf(R.id.navHome, R.id.navTrip, R.id.navCatalog, R.id.navOrders, R.id.navAccount)
        val navIcons = mapOf(
            R.id.navHome to Pair(findViewById<ImageView>(R.id.imgNavHome), findViewById<TextView>(R.id.txtNavHome)),
            R.id.navTrip to Pair(findViewById<ImageView>(R.id.imgNavTrip), findViewById<TextView>(R.id.txtNavTrip)),
            R.id.navCatalog to Pair(findViewById<ImageView>(R.id.imgNavCatalog), findViewById<TextView>(R.id.txtNavCatalog)),
            R.id.navOrders to Pair(findViewById<ImageView>(R.id.imgNavOrders), findViewById<TextView>(R.id.txtNavOrders)),
            R.id.navAccount to Pair(findViewById<ImageView>(R.id.imgNavAccount), findViewById<TextView>(R.id.txtNavAccount))
        )

        // Reset all navigation filters
        val colorPrimary = ContextCompat.getColor(this, R.color.brand_navy)
        val colorMuted = ContextCompat.getColor(this, R.color.text_muted)

        for ((id, pair) in navIcons) {
            if (id == tabId) {
                pair.first.imageTintList = ColorStateList.valueOf(colorPrimary)
                pair.second.setTextColor(colorPrimary)
                pair.second.textStyle(true)
            } else {
                pair.first.imageTintList = ColorStateList.valueOf(colorMuted)
                pair.second.setTextColor(colorMuted)
                pair.second.textStyle(false)
            }
        }

        // Reset views
        tabHome.visibility = View.GONE
        tabTrip.visibility = View.GONE
        tabKatalog.visibility = View.GONE
        tabOrders.visibility = View.GONE
        tabAkun.visibility = View.GONE

        // Switch to correct content
        when (tabId) {
            R.id.navHome -> {
                headerTitle.text = "Beranda"
                tabHome.visibility = View.VISIBLE
                populateHomeContent()
            }
            R.id.navTrip -> {
                headerTitle.text = "Katalog Trip"
                tabTrip.visibility = View.VISIBLE
                populateTripTabContent()
            }
            R.id.navCatalog -> {
                headerTitle.text = "Katalog Produk"
                tabKatalog.visibility = View.VISIBLE
                findViewById<TextView>(R.id.txtKatalogTitle).text = "Semua Produk Jastip"
                populateKatalogTabContent()
            }
            R.id.navOrders -> {
                headerTitle.text = "Pesanan Saya"
                tabOrders.visibility = View.VISIBLE
                populateOrdersTabContent()
            }
            R.id.navAccount -> {
                headerTitle.text = "Akun Saya"
                tabAkun.visibility = View.VISIBLE
            }
        }
    }

    private fun TextView.textStyle(isBold: Boolean) {
        this.typeface = android.graphics.Typeface.defaultFromStyle(
            if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }

    // Realtime search suggestions
    private fun populateSuggestions(
        query: String,
        suggestionsList: LinearLayout,
        suggestionsCard: MaterialCardView,
        searchEdit: EditText
    ) {
        suggestionsList.removeAllViews()
        val allProducts = firebaseSim.getProducts()
        val matched = allProducts.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }

        if (matched.isEmpty()) {
            val emptyView = TextView(this)
            emptyView.text = "Produk tidak ditemukan"
            emptyView.setPadding(12, 12, 12, 12)
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            suggestionsList.addView(emptyView)
            return
        }

        // Limit to 4 suggestions
        for (prod in matched.take(4)) {
            val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, suggestionsList, false) as TextView
            view.text = prod.name
            view.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
            view.textSize = 13f
            view.setOnClickListener {
                searchEdit.setText(prod.name)
                suggestionsCard.visibility = View.GONE
                // Open direct product details page
                openProductDetail(prod)
            }
            suggestionsList.addView(view)
        }
    }

    private fun openProductDetail(product: Product) {
        selectedProduct = product
        
        val imgDetail = findViewById<ImageView>(R.id.imgDetailProduct)
        val txtName = findViewById<TextView>(R.id.txtDetailProductName)
        val txtPrice = findViewById<TextView>(R.id.txtDetailProductPrice)
        val txtTrip = findViewById<TextView>(R.id.txtDetailProductTrip)
        val txtStock = findViewById<TextView>(R.id.txtDetailProductStock)
        val txtDesc = findViewById<TextView>(R.id.txtDetailProductDesc)

        // Set image vector
        val imgRes = when (product.imageType) {
            "bag" -> R.drawable.ic_bag
            "skincare" -> R.drawable.ic_skincare
            "perfume" -> R.drawable.ic_perfume
            else -> R.drawable.ic_bag
        }
        imgDetail.setImageResource(imgRes)
        txtName.text = product.name
        txtPrice.text = formatRupiah(product.price)
        txtDesc.text = product.description
        txtStock.text = "Stok: ${product.stock} barang tersedia"

        // Search trip name
        val trips = firebaseSim.getTrips()
        val trip = trips.firstOrNull { it.id == product.tripId }
        val tripStr = if (trip != null) "Trip: ${trip.destination} | ${trip.dates}" else "Asal Trip: Tidak diketahui"
        txtTrip.text = tripStr

        switchView(detailContainer)
    }

    // Populate Tab 1: Home dashboard contents
    private fun populateHomeContent() {
        val layoutTrips = findViewById<LinearLayout>(R.id.layoutTripHorizontalList)
        val layoutProducts = findViewById<LinearLayout>(R.id.layoutProductHomeList)

        layoutTrips.removeAllViews()
        layoutProducts.removeAllViews()

        // 1. POPULATE HORIZONTAL TRIPS (Korea, Japan, Singapore)
        val trips = firebaseSim.getTrips()
        for (trip in trips) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_trip_horizontal, layoutTrips, false)
            val txtDest = card.findViewById<TextView>(R.id.txtTripDest)
            val txtDates = card.findViewById<TextView>(R.id.txtTripDates)
            val imgTrip = card.findViewById<ImageView>(R.id.imgTripBackground)

            txtDest.text = trip.destination
            txtDates.text = trip.dates

            // Setup active trip tags
            val iconRes = when (trip.imageType) {
                "korea" -> R.drawable.ic_splash_logo
                "japan" -> R.drawable.ic_nav_trip
                "singapore" -> R.drawable.ic_nav_catalog
                else -> R.drawable.ic_splash_logo
            }
            imgTrip.setImageResource(iconRes)
            imgTrip.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_navy))

            card.setOnClickListener {
                // Phase 2: Click Trip -> Open Catalog Tab filtered to show products from that Trip!
                val filteredProducts = firebaseSim.getProducts().filter { it.tripId == trip.id }
                headerTitleText("Jastip ${trip.destination}")
                switchTab(R.id.navCatalog)
                findViewById<TextView>(R.id.txtKatalogTitle).text = "Produk dari ${trip.destination}"
                populateKatalogList(filteredProducts)
            }
            layoutTrips.addView(card)
        }

        // 2. POPULATE VERTICAL RECOMMENDATIONS
        val products = firebaseSim.getProducts().take(3) // Top 3 products
        for (prod in products) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_product_vertical, layoutProducts, false)
            val txtName = item.findViewById<TextView>(R.id.txtProdName)
            val txtPrice = item.findViewById<TextView>(R.id.txtProdPrice)
            val txtOrigin = item.findViewById<TextView>(R.id.txtProdOrigin)
            val imgProd = item.findViewById<ImageView>(R.id.imgProdVector)

            txtName.text = prod.name
            txtPrice.text = formatRupiah(prod.price)

            val originTrip = trips.firstOrNull { it.id == prod.tripId }
            txtOrigin.text = "Trip: ${originTrip?.destination ?: "Import"}"

            val imgRes = when (prod.imageType) {
                "bag" -> R.drawable.ic_bag
                "skincare" -> R.drawable.ic_skincare
                "perfume" -> R.drawable.ic_perfume
                else -> R.drawable.ic_bag
            }
            imgProd.setImageResource(imgRes)

            item.setOnClickListener {
                openProductDetail(prod)
            }
            layoutProducts.addView(item)
        }
    }

    private fun headerTitleText(title: String) {
        findViewById<TextView>(R.id.txtHeaderTitle).text = title
    }

    // Populate Tab 2: All active trips listing
    private fun populateTripTabContent() {
        val layout = findViewById<LinearLayout>(R.id.layoutTripTabList)
        layout.removeAllViews()

        val trips = firebaseSim.getTrips()
        for (trip in trips) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_trip_horizontal, layout, false)
            
            // Adjust card params to fit vertical stacking
            val params = card.layoutParams as LinearLayout.LayoutParams
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            params.height = 100.dpToPx()
            params.setMargins(0, 0, 0, 16.dpToPx())
            card.layoutParams = params

            val txtDest = card.findViewById<TextView>(R.id.txtTripDest)
            val txtDates = card.findViewById<TextView>(R.id.txtTripDates)
            val imgTrip = card.findViewById<ImageView>(R.id.imgTripBackground)

            txtDest.text = trip.destination
            txtDates.text = trip.dates
            txtDest.textSize = 18f
            txtDates.textSize = 13f

            val iconRes = when (trip.imageType) {
                "korea" -> R.drawable.ic_splash_logo
                "japan" -> R.drawable.ic_nav_trip
                "singapore" -> R.drawable.ic_nav_catalog
                else -> R.drawable.ic_splash_logo
            }
            imgTrip.setImageResource(iconRes)
            imgTrip.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_navy))

            card.setOnClickListener {
                val filteredProducts = firebaseSim.getProducts().filter { it.tripId == trip.id }
                headerTitleText("Jastip ${trip.destination}")
                switchTab(R.id.navCatalog)
                findViewById<TextView>(R.id.txtKatalogTitle).text = "Produk dari ${trip.destination}"
                populateKatalogList(filteredProducts)
            }
            layout.addView(card)
        }
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    // Populate Tab 3: All products general catalog
    private fun populateKatalogTabContent() {
        val allProducts = firebaseSim.getProducts()
        populateKatalogList(allProducts)
    }

    private fun populateKatalogList(list: List<Product>) {
        val layout = findViewById<LinearLayout>(R.id.layoutKatalogTabList)
        layout.removeAllViews()

        if (list.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Tidak ada produk dalam katalog ini."
            empty.gravity = android.view.Gravity.CENTER
            empty.setPadding(0, 32.dpToPx(), 0, 0)
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            layout.addView(empty)
            return
        }

        val trips = firebaseSim.getTrips()
        for (prod in list) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_product_vertical, layout, false)
            val txtName = item.findViewById<TextView>(R.id.txtProdName)
            val txtPrice = item.findViewById<TextView>(R.id.txtProdPrice)
            val txtOrigin = item.findViewById<TextView>(R.id.txtProdOrigin)
            val imgProd = item.findViewById<ImageView>(R.id.imgProdVector)

            txtName.text = prod.name
            txtPrice.text = formatRupiah(prod.price)

            val originTrip = trips.firstOrNull { it.id == prod.tripId }
            txtOrigin.text = "Trip: ${originTrip?.destination ?: "Import"}"

            val imgRes = when (prod.imageType) {
                "bag" -> R.drawable.ic_bag
                "skincare" -> R.drawable.ic_skincare
                "perfume" -> R.drawable.ic_perfume
                else -> R.drawable.ic_bag
            }
            imgProd.setImageResource(imgRes)

            item.setOnClickListener {
                openProductDetail(prod)
            }
            layout.addView(item)
        }
    }

    // Populate Tab 4: User Order Tracking List
    private fun populateOrdersTabContent() {
        val layout = findViewById<LinearLayout>(R.id.layoutOrdersTabList)
        layout.removeAllViews()

        val orders = firebaseSim.getOrders().filter { it.userId == currentUser?.id }

        if (orders.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Anda belum memiliki riwayat transaksi."
            empty.gravity = android.view.Gravity.CENTER
            empty.setPadding(0, 32.dpToPx(), 0, 0)
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            layout.addView(empty)
            return
        }

        for (ord in orders) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_order_tracking, layout, false)
            val txtId = item.findViewById<TextView>(R.id.txtOrderListId)
            val txtProd = item.findViewById<TextView>(R.id.txtOrderListProduct)
            val txtTotal = item.findViewById<TextView>(R.id.txtOrderListTotal)
            val txtStatus = item.findViewById<TextView>(R.id.txtOrderListStatus)
            val badge = item.findViewById<MaterialCardView>(R.id.cardOrderListStatusBadge)

            txtId.text = ord.id
            txtProd.text = "${ord.productName} (x${ord.qty})"
            txtTotal.text = formatRupiah(ord.grandTotal)
            txtStatus.text = ord.status

            // Configure badge styles depending on status
            val colors = getStatusColors(ord.status)
            badge.setCardBackgroundColor(ColorStateList.valueOf(colors.first))
            txtStatus.setTextColor(colors.second)

            item.setOnClickListener {
                openOrderTracking(ord)
            }
            layout.addView(item)
        }
    }

    private fun getStatusColors(status: String): Pair<Int, Int> {
        return when (status) {
            "Menunggu" -> Pair(ContextCompat.getColor(this, R.color.status_pending_bg), ContextCompat.getColor(this, R.color.status_pending))
            "Diproses" -> Pair(ContextCompat.getColor(this, R.color.status_processing_bg), ContextCompat.getColor(this, R.color.status_processing))
            "Dikirim" -> Pair(ContextCompat.getColor(this, R.color.status_shipped_bg), ContextCompat.getColor(this, R.color.status_shipped))
            "Selesai" -> Pair(ContextCompat.getColor(this, R.color.status_completed_bg), ContextCompat.getColor(this, R.color.status_completed))
            else -> Pair(Color.LTGRAY, Color.BLACK)
        }
    }

    // --- PHASE 2: PRODUCT DETAIL ---
    private fun setupDetail() {
        findViewById<ImageView>(R.id.btnDetailBack).setOnClickListener {
            switchView(homeContainer)
        }

        findViewById<Button>(R.id.btnPesanSekarang).setOnClickListener {
            // Direct Buy (No Cart) -> Switches to Checkout
            if (selectedProduct != null) {
                openCheckout(selectedProduct!!)
            }
        }
    }

    // --- PHASE 3: TRANSAKSI (DIRECT BUY & UPLOAD PAYMENT PROOF) ---
    private fun setupCheckout() {
        findViewById<ImageView>(R.id.btnCheckoutBack).setOnClickListener {
            switchView(detailContainer)
        }

        val btnMinus = findViewById<Button>(R.id.btnQtyMinus)
        val btnPlus = findViewById<Button>(R.id.btnQtyPlus)
        val txtCount = findViewById<TextView>(R.id.txtQtyCount)
        val rgPayment = findViewById<RadioGroup>(R.id.rgPaymentMethods)

        // Upload panels binding
        val cardProof = findViewById<MaterialCardView>(R.id.cardPaymentProof)
        val btnTriggerUpload = findViewById<LinearLayout>(R.id.btnTriggerUpload)
        val layoutProgress = findViewById<LinearLayout>(R.id.layoutUploadProgress)
        val uploadProgress = findViewById<ProgressBar>(R.id.progressProofUpload)
        val uploadPercentText = findViewById<TextView>(R.id.txtUploadPercent)
        val layoutSuccess = findViewById<LinearLayout>(R.id.layoutUploadSuccess)
        val txtProofTitle = findViewById<TextView>(R.id.txtProofTitle)
        val txtProofSub = findViewById<TextView>(R.id.txtProofSub)

        // Adjust quantity logic
        btnMinus.setOnClickListener {
            if (currentCheckoutQty > 1) {
                currentCheckoutQty--
                txtCount.text = currentCheckoutQty.toString()
                recalculateCheckoutTotals()
            }
        }

        btnPlus.setOnClickListener {
            if (selectedProduct != null && currentCheckoutQty < selectedProduct!!.stock) {
                currentCheckoutQty++
                txtCount.text = currentCheckoutQty.toString()
                recalculateCheckoutTotals()
            } else {
                Toast.makeText(this, "Jumlah barang melebihi stok tersedia!", Toast.LENGTH_SHORT).show()
            }
        }

        // Payment method toggle logic
        rgPayment.setOnCheckedChangeListener { _, checkedId ->
            isProofUploaded = false
            uploadedProofUrl = null
            layoutSuccess.visibility = View.GONE
            layoutProgress.visibility = View.GONE
            findViewById<LinearLayout>(R.id.btnTriggerUpload).visibility = View.VISIBLE

            when (checkedId) {
                R.id.rbCOD -> {
                    cardProof.visibility = View.GONE
                }
                R.id.rbFullPayment -> {
                    cardProof.visibility = View.VISIBLE
                    txtProofTitle.text = "Upload Bukti Transfer (Pelunasan 100%)"
                    txtProofSub.text = "Transfer sisa Grand Total ke Bank BCA JASTARA: 123-456-789"
                }
                R.id.rbDP50 -> {
                    cardProof.visibility = View.VISIBLE
                    txtProofTitle.text = "Upload Bukti Transfer (Uang Muka DP 50%)"
                    txtProofSub.text = "Transfer 50% dari Grand Total ke Bank BCA JASTARA: 123-456-789"
                }
            }
            recalculateCheckoutTotals()
        }

        // Triggering Simulated Payment Upload to Storage
        btnTriggerUpload.setOnClickListener {
            btnTriggerUpload.visibility = View.GONE
            layoutProgress.visibility = View.VISIBLE
            uploadProgress.progress = 0
            uploadPercentText.text = "Mengunggah ke Firebase Storage: 0%"

            firebaseSim.uploadProofSim(
                onProgress = { progress ->
                    uploadProgress.progress = progress
                    uploadPercentText.text = "Mengunggah ke Firebase Storage: $progress%"
                },
                onComplete = { mockUrl ->
                    layoutProgress.visibility = View.GONE
                    layoutSuccess.visibility = View.VISIBLE
                    isProofUploaded = true
                    uploadedProofUrl = mockUrl
                    Toast.makeText(this, "Bukti transfer berhasil divalidasi!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Place final order click
        findViewById<Button>(R.id.btnConfirmCheckout).setOnClickListener {
            val address = findViewById<EditText>(R.id.edtCheckoutAddress).text.toString().trim()
            val notes = findViewById<EditText>(R.id.edtCheckoutNotes).text.toString().trim()

            if (address.isEmpty()) {
                Toast.makeText(this, "Alamat pengiriman wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val checkedMethodId = rgPayment.checkedRadioButtonId
            val methodStr = when (checkedMethodId) {
                R.id.rbCOD -> "COD"
                R.id.rbFullPayment -> "FULL"
                R.id.rbDP50 -> "DP50"
                else -> "COD"
            }

            // Transfer full / DP 50% checks
            if (methodStr != "COD" && !isProofUploaded) {
                Toast.makeText(this, "Silakan unggah bukti transfer pembayaran Anda terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Calculate exact pricing
            val price = selectedProduct!!.price
            val totalProd = price * currentCheckoutQty
            val ship = 20000.0
            val grand = totalProd + ship

            // Set initial order status
            val initialStatus = "Menunggu"

            val order = Order(
                id = "", // set by database engine
                userId = currentUser?.id ?: "",
                productId = selectedProduct!!.id,
                productName = selectedProduct!!.name,
                productPrice = price,
                qty = currentCheckoutQty,
                address = address,
                notes = notes,
                shippingFee = ship,
                grandTotal = grand,
                paymentMethod = methodStr,
                status = initialStatus,
                paymentProofUrl = uploadedProofUrl,
                balanceProofUrl = null,
                isBalancePaid = false
            )

            findViewById<Button>(R.id.btnConfirmCheckout).isEnabled = false
            findViewById<Button>(R.id.btnConfirmCheckout).text = "Menyimpan ke Firestore..."

            firebaseSim.createOrder(order) { success, orderId ->
                findViewById<Button>(R.id.btnConfirmCheckout).isEnabled = true
                findViewById<Button>(R.id.btnConfirmCheckout).text = "Konfirmasi & Bayar"

                if (success) {
                    Toast.makeText(this, "Order berhasil disimpan ke Firestore collection 'Orders'!", Toast.LENGTH_LONG).show()
                    
                    // Clear checkout fields
                    findViewById<EditText>(R.id.edtCheckoutAddress).text.clear()
                    findViewById<EditText>(R.id.edtCheckoutNotes).text.clear()

                    // Redirect directly to Halaman Tracking
                    val ordersList = firebaseSim.getOrders()
                    val createdOrder = ordersList.firstOrNull { it.id == orderId }
                    if (createdOrder != null) {
                        openOrderTracking(createdOrder)
                    } else {
                        switchTab(R.id.navOrders)
                        switchView(homeContainer)
                    }
                } else {
                    Toast.makeText(this, "Gagal membuat pesanan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openCheckout(product: Product) {
        selectedProduct = product
        currentCheckoutQty = 1
        isProofUploaded = false
        uploadedProofUrl = null

        // Default widgets settings
        findViewById<TextView>(R.id.txtQtyCount).text = "1"
        findViewById<ImageView>(R.id.imgCheckoutProduct).setImageResource(
            when (product.imageType) {
                "bag" -> R.drawable.ic_bag
                "skincare" -> R.drawable.ic_skincare
                "perfume" -> R.drawable.ic_perfume
                else -> R.drawable.ic_bag
            }
        )
        findViewById<TextView>(R.id.txtCheckoutProductName).text = product.name
        findViewById<TextView>(R.id.txtCheckoutProductPrice).text = formatRupiah(product.price)
        
        // Reset inputs
        findViewById<EditText>(R.id.edtCheckoutAddress).text.clear()
        findViewById<EditText>(R.id.edtCheckoutNotes).text.clear()

        // Reset radio button to COD
        findViewById<RadioButton>(R.id.rbCOD).isChecked = true
        findViewById<MaterialCardView>(R.id.cardPaymentProof).visibility = View.GONE
        findViewById<LinearLayout>(R.id.layoutUploadSuccess).visibility = View.GONE

        recalculateCheckoutTotals()
        switchView(checkoutContainer)
    }

    private fun recalculateCheckoutTotals() {
        if (selectedProduct == null) return

        val price = selectedProduct!!.price
        val totalProd = price * currentCheckoutQty
        val ship = 20000.0
        val grand = totalProd + ship

        findViewById<TextView>(R.id.txtSummaryProductLabel).text = "${selectedProduct!!.name} (x$currentCheckoutQty)"
        findViewById<TextView>(R.id.txtSummaryProductPrice).text = formatRupiah(totalProd)
        findViewById<TextView>(R.id.txtSummaryShipping).text = formatRupiah(ship)

        val rgPayment = findViewById<RadioGroup>(R.id.rgPaymentMethods)
        val isDp50 = rgPayment.checkedRadioButtonId == R.id.rbDP50

        if (isDp50) {
            val dpValue = grand / 2.0
            findViewById<TextView>(R.id.txtSummaryGrandTotal).text = "${formatRupiah(grand)}\n(DP 50%: ${formatRupiah(dpValue)})"
        } else {
            findViewById<TextView>(R.id.txtSummaryGrandTotal).text = formatRupiah(grand)
        }
    }

    // --- PHASE 4: TRACKING & PELUNASAN ---
    private fun setupTracking() {
        findViewById<ImageView>(R.id.btnTrackingBack).setOnClickListener {
            // go back to home tab orders
            switchTab(R.id.navOrders)
            switchView(homeContainer)
        }

        // Setup Dev Admin Simulation buttons inside Tracking Screen!
        findViewById<Button>(R.id.btnDevSetPending).setOnClickListener { updateTrackingStatusSim("Menunggu") }
        findViewById<Button>(R.id.btnDevSetProcessing).setOnClickListener { updateTrackingStatusSim("Diproses") }
        findViewById<Button>(R.id.btnDevSetShipped).setOnClickListener { updateTrackingStatusSim("Dikirim") }
        findViewById<Button>(R.id.btnDevSetCompleted).setOnClickListener { updateTrackingStatusSim("Selesai") }

        // Setup pelunasan payment uploader
        val btnSettlement = findViewById<Button>(R.id.btnBayarPelunasan)
        val uploaderLayout = findViewById<LinearLayout>(R.id.layoutPelunasanUploader)
        val triggerUpload = findViewById<LinearLayout>(R.id.btnTriggerPelunasanUpload)
        val progressLayout = findViewById<LinearLayout>(R.id.layoutPelunasanProgress)
        val uploadProgress = findViewById<ProgressBar>(R.id.progressPelunasanUpload)
        val uploadStatusText = findViewById<TextView>(R.id.txtPelunasanUploadStatus)

        btnSettlement.setOnClickListener {
            uploaderLayout.visibility = View.VISIBLE
        }

        triggerUpload.setOnClickListener {
            triggerUpload.visibility = View.GONE
            progressLayout.visibility = View.VISIBLE
            uploadProgress.progress = 0
            uploadStatusText.text = "Mengunggah bukti pelunasan..."

            firebaseSim.uploadProofSim(
                onProgress = { progress ->
                    uploadProgress.progress = progress
                },
                onComplete = { proofUrl ->
                    if (trackingOrder != null) {
                        firebaseSim.uploadBalancePayment(trackingOrder!!.id, proofUrl) { success ->
                            if (success) {
                                Toast.makeText(this, "Pembayaran pelunasan sukses!", Toast.LENGTH_SHORT).show()
                                
                                // Fetch updated state from Firestore Sim
                                val updatedOrders = firebaseSim.getOrders()
                                val updated = updatedOrders.firstOrNull { it.id == trackingOrder!!.id }
                                if (updated != null) {
                                    openOrderTracking(updated)
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun openOrderTracking(order: Order) {
        trackingOrder = order

        findViewById<TextView>(R.id.txtTrackingOrderId).text = "Order ID: ${order.id}"
        findViewById<TextView>(R.id.txtTrackingStatus).text = order.status

        // Set status colors and badge
        val badge = findViewById<MaterialCardView>(R.id.cardTrackingStatusBadge)
        val colors = getStatusColors(order.status)
        badge.setCardBackgroundColor(ColorStateList.valueOf(colors.first))
        findViewById<TextView>(R.id.txtTrackingStatus).setTextColor(colors.second)

        // Set products description
        findViewById<TextView>(R.id.txtTrackingItemLabel).text = "${order.productName} (x${order.qty})"
        findViewById<TextView>(R.id.txtTrackingItemPrice).text = formatRupiah(order.productPrice * order.qty)
        findViewById<TextView>(R.id.txtTrackingShipping).text = formatRupiah(order.shippingFee)
        findViewById<TextView>(R.id.txtTrackingGrandTotal).text = formatRupiah(order.grandTotal)
        findViewById<TextView>(R.id.txtTrackingPayMethod).text = when (order.paymentMethod) {
            "COD" -> "COD (Bayar di Tempat)"
            "FULL" -> "Transfer Full"
            "DP50" -> if (order.isBalancePaid) "DP 50% (Lunas)" else "DP 50% (Belum Lunas)"
            else -> order.paymentMethod
        }
        findViewById<TextView>(R.id.txtTrackingAddress).text = "Alamat: ${order.address}\nCatatan: ${if (order.notes.isEmpty()) "-" else order.notes}"

        // Update active pipeline timeline highlighting (Phase 4 tracking progression)
        updatePipelineVisuals(order.status)

        // Configure conditional panels: COD, Full payment vs DP 50%
        val layoutStd = findViewById<LinearLayout>(R.id.layoutTrackingStandardStatus)
        val layoutDpWaiting = findViewById<LinearLayout>(R.id.layoutTrackingDpWaiting)
        val layoutDpSettlement = findViewById<LinearLayout>(R.id.layoutTrackingDpSettlement)
        val layoutSuccess = findViewById<LinearLayout>(R.id.layoutTrackingSuccessState)

        // Hide all initial
        layoutStd.visibility = View.GONE
        layoutDpWaiting.visibility = View.GONE
        layoutDpSettlement.visibility = View.GONE
        layoutSuccess.visibility = View.GONE

        // Hide pelunasan inner uploader
        findViewById<LinearLayout>(R.id.layoutPelunasanUploader).visibility = View.GONE
        findViewById<LinearLayout>(R.id.btnTriggerPelunasanUpload).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.layoutPelunasanProgress).visibility = View.GONE

        if (order.status == "Selesai") {
            layoutSuccess.visibility = View.VISIBLE
        } else if (order.paymentMethod == "DP50") {
            if (order.status == "Dikirim") {
                // Phase 4: DP 50% - Dikirim / Tiba -> Show Bayar Pelunasan sisa tagihan!
                layoutDpSettlement.visibility = View.VISIBLE
                val sisaValue = order.grandTotal / 2.0
                findViewById<TextView>(R.id.txtSisaTagihanValue).text = formatRupiah(sisaValue)
            } else {
                // Pending, processing -> waiting
                layoutDpWaiting.visibility = View.VISIBLE
            }
        } else {
            // COD or Transfer Full -> Just standard delivery tracking status
            layoutStd.visibility = View.VISIBLE
        }

        switchView(trackingContainer)
    }

    private fun updatePipelineVisuals(status: String) {
        val colorActive = ContextCompat.getColor(this, R.color.brand_navy)
        val colorInactive = ContextCompat.getColor(this, R.color.text_muted)
        val colorBgActive = ContextCompat.getColor(this, R.color.brand_navy_light)
        val colorBgInactive = ContextCompat.getColor(this, R.color.bg_gray)

        // Views
        val imgPending = findViewById<ImageView>(R.id.imgStepPending)
        val txtPending = findViewById<TextView>(R.id.txtStepPending)

        val imgProcessing = findViewById<ImageView>(R.id.imgStepProcessing)
        val txtProcessing = findViewById<TextView>(R.id.txtStepProcessing)

        val imgShipped = findViewById<ImageView>(R.id.imgStepShipped)
        val txtShipped = findViewById<TextView>(R.id.txtStepShipped)

        val imgCompleted = findViewById<ImageView>(R.id.imgStepCompleted)
        val txtCompleted = findViewById<TextView>(R.id.txtStepCompleted)

        val line1 = findViewById<View>(R.id.lineStep1)
        val line2 = findViewById<View>(R.id.lineStep2)
        val line3 = findViewById<View>(R.id.lineStep3)

        // Clear all states first
        val pairs = listOf(
            Triple(imgPending, txtPending, "Menunggu"),
            Triple(imgProcessing, txtProcessing, "Diproses"),
            Triple(imgShipped, txtShipped, "Dikirim"),
            Triple(imgCompleted, txtCompleted, "Selesai")
        )

        for (triple in pairs) {
            triple.first.imageTintList = ColorStateList.valueOf(colorInactive)
            triple.first.backgroundTintList = ColorStateList.valueOf(colorBgInactive)
            triple.second.setTextColor(colorInactive)
            triple.second.textStyle(false)
        }
        line1.setBackgroundColor(colorBgInactive)
        line2.setBackgroundColor(colorBgInactive)
        line3.setBackgroundColor(colorBgInactive)

        // Highlight based on current status sequence
        when (status) {
            "Menunggu" -> {
                imgPending.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_pending))
                imgPending.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_pending_bg))
                txtPending.setTextColor(ContextCompat.getColor(this, R.color.status_pending))
                txtPending.textStyle(true)
            }
            "Diproses" -> {
                // Pending & Processing highlighted
                imgPending.imageTintList = ColorStateList.valueOf(colorActive)
                imgPending.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtPending.setTextColor(colorActive)

                imgProcessing.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_processing))
                imgProcessing.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_processing_bg))
                txtProcessing.setTextColor(ContextCompat.getColor(this, R.color.status_processing))
                txtProcessing.textStyle(true)

                line1.setBackgroundColor(colorActive)
            }
            "Dikirim" -> {
                // Pending, Processing & Shipped highlighted
                imgPending.imageTintList = ColorStateList.valueOf(colorActive)
                imgPending.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtPending.setTextColor(colorActive)

                imgProcessing.imageTintList = ColorStateList.valueOf(colorActive)
                imgProcessing.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtProcessing.setTextColor(colorActive)

                imgShipped.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_shipped))
                imgShipped.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_shipped_bg))
                txtShipped.setTextColor(ContextCompat.getColor(this, R.color.status_shipped))
                txtShipped.textStyle(true)

                line1.setBackgroundColor(colorActive)
                line2.setBackgroundColor(colorActive)
            }
            "Selesai" -> {
                // All highlighted
                imgPending.imageTintList = ColorStateList.valueOf(colorActive)
                imgPending.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtPending.setTextColor(colorActive)

                imgProcessing.imageTintList = ColorStateList.valueOf(colorActive)
                imgProcessing.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtProcessing.setTextColor(colorActive)

                imgShipped.imageTintList = ColorStateList.valueOf(colorActive)
                imgShipped.backgroundTintList = ColorStateList.valueOf(colorBgActive)
                txtShipped.setTextColor(colorActive)

                imgCompleted.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_completed))
                imgCompleted.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_completed_bg))
                txtCompleted.setTextColor(ContextCompat.getColor(this, R.color.status_completed))
                txtCompleted.textStyle(true)

                line1.setBackgroundColor(colorActive)
                line2.setBackgroundColor(colorActive)
                line3.setBackgroundColor(colorActive)
            }
        }
    }

    // Handles Dev Console Sim updates in the tracking page
    private fun updateTrackingStatusSim(newStatus: String) {
        if (trackingOrder != null) {
            firebaseSim.updateOrderStatus(trackingOrder!!.id, newStatus) { success ->
                if (success) {
                    Toast.makeText(this, "Simulasi Admin: Status berhasil diubah ke '$newStatus'!", Toast.LENGTH_SHORT).show()
                    
                    // Fetch updated from DB
                    val ordersList = firebaseSim.getOrders()
                    val updated = ordersList.firstOrNull { it.id == trackingOrder!!.id }
                    if (updated != null) {
                        openOrderTracking(updated)
                    }
                }
            }
        }
    }

    // Formatting utilities
    private fun formatRupiah(value: Double): String {
        val formatter = java.text.DecimalFormat("#,###")
        val symbols = formatter.decimalFormatSymbols
        symbols.groupingSeparator = '.'
        formatter.decimalFormatSymbols = symbols
        return "Rp " + formatter.format(value)
    }
}
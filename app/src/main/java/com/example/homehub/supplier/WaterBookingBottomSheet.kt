import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.homehub.billing.MpesaService
import com.example.homehub.supplier.WaterSupplier
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import android.widget.RadioGroup
import android.widget.ProgressBar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.homehub.R
import com.example.homehub.student.WaterOrdersActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WaterBookingBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_SUPPLIER = "supplier"

        fun newInstance(supplier: WaterSupplier): WaterBookingBottomSheet {
            val fragment = WaterBookingBottomSheet()
            val args = Bundle()
            args.putParcelable(ARG_SUPPLIER, supplier)
            fragment.arguments = args
            return fragment
        }
    }

    private var supplier: WaterSupplier? = null
    private var currentLitres = 20
    private var isPolling = false
    private var checkoutRequestId: String? = null
    private var finalAmount: Double = 0.0
    private var studentName: String = "Student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supplier = arguments?.getParcelable(ARG_SUPPLIER)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_water_booking, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val supplier = this.supplier ?: run {
            dismiss()
            return
        }

        val pricePerLitre = supplier.pricePerLitre.takeIf { it > 0 } ?: supplier.drinkingPrice
        val deliveryFee = supplier.deliveryFee

        // Supplier info
        view.findViewById<TextView>(R.id.tvSupplierName).text =
            supplier.businessName.ifBlank { supplier.name }
        view.findViewById<TextView>(R.id.tvSupplierArea).text =
            supplier.serviceArea.ifBlank { "Service area not set" }
        view.findViewById<TextView>(R.id.tvSupplierPhone).text =
            supplier.phone.ifBlank { "Contact not provided" }
        // Supplier hero stats
        view.findViewById<TextView>(R.id.pricePerLitreText).text =
            "KSh ${String.format("%,.0f", pricePerLitre)} /Litre"
        view.findViewById<TextView>(R.id.deliveredCountText).text =
            "${supplier.deliveredCount} Delivered"

        // Litres input
        val etLitres = view.findViewById<TextInputEditText>(R.id.etLitres)
        val btnDecrease = view.findViewById<MaterialButton>(R.id.btnDecrease)
        val btnIncrease = view.findViewById<MaterialButton>(R.id.btnIncrease)

        // Address & Phone
        val etAddress = view.findViewById<TextInputEditText>(R.id.etAddress)
        val etPhone = view.findViewById<TextInputEditText>(R.id.etPhone)

        // Total fields
        val tvWaterCostLabel = view.findViewById<TextView>(R.id.tvWaterCostLabel)
        val tvWaterCost = view.findViewById<TextView>(R.id.tvWaterCost)
        val tvDeliveryCost = view.findViewById<TextView>(R.id.tvDeliveryCost)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvTotalAmount)

        val btnPlaceOrder = view.findViewById<MaterialButton>(R.id.btnPlaceOrder)

        // Auto-fetch phone from student profile
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        studentName = doc.getString("fullName") ?: doc.getString("username") ?: "Student"
                        val phone = doc.getString("phone") ?: ""
                        if (phone.isNotBlank() && etPhone.text.toString().isEmpty()) {
                            etPhone.setText(phone)
                        }
                    }
                }
        }

        fun updateInteractions(enabled: Boolean = true) {
            btnPlaceOrder.isEnabled = enabled
            etLitres.isEnabled = enabled
            btnIncrease.isEnabled = enabled
            btnDecrease.isEnabled = enabled
            view.findViewById<RadioGroup>(R.id.rgPaymentMethod).isEnabled = enabled
            view.findViewById<View>(R.id.rbMpesa).isEnabled = enabled
            view.findViewById<View>(R.id.rbPOD).isEnabled = enabled
        }

        // Calculate total
        fun updateTotal() {
            val litres = etLitres.text.toString().toIntOrNull() ?: 0
            currentLitres = litres
            val waterCost = litres * pricePerLitre
            finalAmount = waterCost + deliveryFee

            tvWaterCostLabel.text = "Water (${litres}L × KSh ${String.format("%,.0f", pricePerLitre)})"
            tvWaterCost.text = "KSh ${String.format("%,.0f", waterCost)}"
            tvDeliveryCost.text = "KSh ${String.format("%,.0f", deliveryFee)}"
            tvTotalAmount.text = "KSh ${String.format("%,.0f", finalAmount)}"
            
            val paymentMethod = view.findViewById<RadioGroup>(R.id.rgPaymentMethod).checkedRadioButtonId
            if (paymentMethod == R.id.rbPOD) {
                btnPlaceOrder.text = "Place Order (COD) — KSh ${String.format("%,.0f", finalAmount)}"
            } else {
                btnPlaceOrder.text = "Confirm & Pay — KSh ${String.format("%,.0f", finalAmount)}"
            }
        }

        view.findViewById<RadioGroup>(R.id.rgPaymentMethod).setOnCheckedChangeListener { _, _ -> updateInteractions(); updateTotal() }

        etLitres.setText(currentLitres.toString())
        updateTotal()

        btnDecrease.setOnClickListener {
            val current = etLitres.text.toString().toIntOrNull() ?: 0
            if (current > 5) {
                etLitres.setText((current - 5).toString())
                updateTotal()
            }
        }

        btnIncrease.setOnClickListener {
            val current = etLitres.text.toString().toIntOrNull() ?: 0
            etLitres.setText((current + 5).toString())
            updateTotal()
        }

        etLitres.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotal()
            }
        })

        // Place order
        btnPlaceOrder.setOnClickListener {
            val litres = etLitres.text.toString().toIntOrNull() ?: 0
            val address = etAddress.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (litres <= 0) {
                etLitres.error = "Enter valid quantity"
                etLitres.requestFocus()
                return@setOnClickListener
            }
            if (address.isEmpty()) {
                etAddress.error = "Enter delivery address"
                etAddress.requestFocus()
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                etPhone.error = "Phone number required"
                etPhone.requestFocus()
                return@setOnClickListener
            }

            val isMpesa = view.findViewById<RadioGroup>(R.id.rgPaymentMethod).checkedRadioButtonId == R.id.rbMpesa
            
            if (isMpesa) {
                processMpesaPayment(phone, address)
            } else {
                createWaterOrder("CASH_ON_DELIVERY", "POD_${System.currentTimeMillis()}", address, phone)
            }
        }
    }

    private fun processMpesaPayment(phone: String, address: String) {
        val cardStatus = view?.findViewById<MaterialCardView>(R.id.cardPollingStatus)
        val tvStatus = view?.findViewById<TextView>(R.id.tvPollingStatus)
        val progressBar = view?.findViewById<ProgressBar>(R.id.pbPolling)
        val btnPlaceOrder = view?.findViewById<MaterialButton>(R.id.btnPlaceOrder)
        
        cardStatus?.visibility = View.VISIBLE
        tvStatus?.text = "Connecting to M-Pesa..."
        progressBar?.isIndeterminate = true
        
        // Disable UI
        btnPlaceOrder?.isEnabled = false
        btnPlaceOrder?.alpha = 0.6f
        
        lifecycleScope.launch {
            try {
                val amountInt = finalAmount.toInt()
                val accountRef = "HH-${supplier?.id?.take(6) ?: "WATER"}"
                tvStatus?.text = "Requesting STK Push..."
                
                val response = MpesaService.sendSTKPushAsync(phone, amountInt, accountRef)
                
                if (response != null && response.responseCode == "0") {
                    tvStatus?.text = "📱 Enter M-Pesa PIN on your phone"
                    startPolling(response.checkoutRequestID, address, phone)
                } else {
                    cardStatus?.visibility = View.GONE
                    btnPlaceOrder?.isEnabled = true
                    btnPlaceOrder?.alpha = 1.0f
                    val errorMsg = response?.customerMessage ?: "Connection failed. Please try again."
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                cardStatus?.visibility = View.GONE
                btnPlaceOrder?.isEnabled = true
                btnPlaceOrder?.alpha = 1.0f
                Toast.makeText(context, "M-Pesa Service Unavailable: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPolling(checkoutId: String, address: String, phone: String) {
        checkoutRequestId = checkoutId
        isPolling = true
        val tvStatus = view?.findViewById<TextView>(R.id.tvPollingStatus)
        val cardStatus = view?.findViewById<MaterialCardView>(R.id.cardPollingStatus)
        val btnPlaceOrder = view?.findViewById<MaterialButton>(R.id.btnPlaceOrder)

        lifecycleScope.launch {
            var attempts = 0
            val maxAttempts = 40 // Approximately 2 minutes

            while (isPolling && attempts < maxAttempts) {
                delay(3000)
                attempts++
                
                val result = MpesaService.querySTKStatusAsync(checkoutId)
                if (result != null) {
                    val rawCode = result.resultCode?.toString()?.trim() ?: ""
                    val resultCode = if (rawCode.contains(".")) rawCode.substringBefore(".") else rawCode
                    val resultDesc = result.resultDesc ?: ""

                    if (resultCode == "0" || resultDesc.contains("success", ignoreCase = true)) {
                        isPolling = false
                        val mpesaReceipt = resultDesc.substringAfter("Receipt No. ", "").substringBefore(" ")
                        val finalReceipt = if (mpesaReceipt.length >= 8) mpesaReceipt else "MPESA_${System.currentTimeMillis()}"
                        
                        tvStatus?.text = "✨ Payment Confirmed! ✨"
                        tvStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
                        delay(1000)
                        createWaterOrder("M-PESA", finalReceipt, address, phone)
                        return@launch
                    } else if (resultCode.isNotEmpty() && resultCode != "null") {
                        isPolling = false
                        cardStatus?.visibility = View.GONE
                        btnPlaceOrder?.isEnabled = true
                        btnPlaceOrder?.alpha = 1.0f
                        
                        val friendlyError = when(resultCode) {
                            "1032" -> "Transaction cancelled by user."
                            "1" -> "Insufficient balance in M-Pesa."
                            "2001" -> "Wrong M-Pesa PIN."
                            "1037" -> "Request timed out."
                            else -> resultDesc
                        }
                        Toast.makeText(context, friendlyError, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }
                
                val dots = ".".repeat(attempts % 3 + 1)
                tvStatus?.text = "Confirming payment$dots"
            }

            if (isPolling) {
                isPolling = false
                cardStatus?.visibility = View.GONE
                btnPlaceOrder?.isEnabled = true
                btnPlaceOrder?.alpha = 1.0f
                Toast.makeText(context, "Payment confirmation timed out. If you were charged, please contact support.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createWaterOrder(method: String, transactionId: String, address: String, phone: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val supplier = this.supplier ?: return
        
        val tvStatus = view?.findViewById<TextView>(R.id.tvPollingStatus)
        tvStatus?.text = "Placing your order..."
        view?.findViewById<MaterialCardView>(R.id.cardPollingStatus)?.visibility = View.VISIBLE

        val orderId = db.collection("waterOrders").document().id
        val now = Date()

        val orderData = hashMapOf(
            "orderId" to orderId,
            "supplierId" to supplier.id,
            "studentId" to currentUser.uid,
            "studentName" to studentName,
            "amount" to finalAmount,
            "quantity" to "${currentLitres} L",
            "status" to if (method == "CASH_ON_DELIVERY") "pending_cod" else "paid",
            "waterType" to "Clean Water",
            "paymentMethod" to method,
            "deliveryAddress" to address,
            "contactPhone" to phone,
            "transactionId" to transactionId,
            "timestamp" to now
        )

        db.runTransaction { transaction ->
            val supplierRef = db.collection("users").document(supplier.id)
            val supplierSnap = transaction.get(supplierRef)
            val currentStock = (supplierSnap.getLong("stockLiters") ?: 1000L).toInt()
            val newStock = (currentStock - currentLitres).coerceAtLeast(0)
            transaction.update(supplierRef, "stockLiters", newStock)
            transaction.set(db.collection("waterOrders").document(orderId), orderData)
        }.addOnSuccessListener {
            // Notifications
            com.example.homehub.utils.NotificationManager.sendWaterOrderNotification(
                supplier.id, studentName, finalAmount, "${currentLitres} L"
            )
            com.example.homehub.utils.NotificationManager.sendWaterOrderReceiptNotification(
                currentUser.uid, supplier.businessName.ifEmpty { supplier.name }, finalAmount, transactionId
            )

            if (isAdded) {
                Toast.makeText(context, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), WaterOrdersActivity::class.java))
                dismiss()
            }
        }.addOnFailureListener { e ->
            tvStatus?.text = "Error placing order"
            (view?.findViewById<MaterialButton>(R.id.btnPlaceOrder))?.isEnabled = true
            Toast.makeText(context, "Order failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}

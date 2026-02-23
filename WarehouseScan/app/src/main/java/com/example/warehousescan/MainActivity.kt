package com.example.warehousescan

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var tasksContainer: LinearLayout
    private lateinit var ipInput: EditText
    private lateinit var statusText: TextView
    private lateinit var radioReceiving: RadioButton
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tasksContainer = findViewById(R.id.tasksContainer)
        ipInput = findViewById(R.id.ipInput)
        statusText = findViewById(R.id.txtStatus)
        radioReceiving = findViewById(R.id.radioReceiving)

        val btnScan: Button = findViewById(R.id.btnScan)
        val btnRefresh: Button = findViewById(R.id.btnRefresh)

        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        ipInput.setText(prefs.getString("server_ip", ""))

        btnRefresh.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            prefs.edit().putString("server_ip", ip).apply()
            fetchTasks(ip)
        }

        btnScan.setOnClickListener {
            IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Сканирование...")
                initiateScan()
            }
        }
    }

    private fun fetchTasks(ip: String) {
        val request = Request.Builder().url("http://$ip:3000/api/tasks").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Ошибка связи с $ip"; statusText.setTextColor(Color.RED) }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    statusText.text = "✅ Подключено к $ip"; statusText.setTextColor(Color.parseColor("#4CAF50"))
                    val jsonArray = JSONArray(body)
                    tasksContainer.removeAllViews()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val tv = TextView(this@MainActivity).apply {
                            text = "${if(obj.getString("type")=="receiving") "📦" else "🛒"} ${obj.getString("title")}\n${obj.getInt("progress")} / ${obj.getInt("total")}"
                            setPadding(30, 30, 30, 30)
                            textSize = 16f
                            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                        }
                        tasksContainer.addView(tv)
                    }
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result?.contents != null) sendScanToServer(result.contents)
        else super.onActivityResult(requestCode, resultCode, data)
    }

    private fun sendScanToServer(qrCode: String) {
        val ip = ipInput.text.toString().trim()
        val scanType = if (radioReceiving.isChecked) "receiving" else "picking"

        val json = JSONObject().apply {
            put("qrCode", qrCode)
            put("workerName", "Android_User")
            put("scanType", scanType)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("http://$ip:3000/api/scan").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Ошибка отправки", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Отправлено (${if(scanType=="receiving") "Приемка" else "Сборка"})", Toast.LENGTH_SHORT).show()
                    fetchTasks(ip)
                }
            }
        })
    }
}
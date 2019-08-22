package com.example.tthellopackage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bt_call.setOnClickListener {
            GlobalScope.launch {
                runOnUiThread {
                    loadingVisibility(true)
                }
                try {
                    val response = getRoom(RoomRequest(USER_NAME))
                    if (response.isSuccessful && response.body() != null) {
                        val room = response.body()!!.room
                        startToCall(
                            HOST_URL,
                            response.body()!!.meta.token,
                            room.name,
                            USER_NAME)
                    } else {
                        //Handle unsuccessful response
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showError(e.message ?: "")
                    }
                }
                runOnUiThread {
                    loadingVisibility(false)
                }
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    }

    private fun startToCall(
        host: String, token: String, resourceId: String, userName: String
    ) {
        startActivityForResult(
            SecondActivity.intent(
                this, host, token, resourceId, userName
            ),
            REQUEST_CODE
        )
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://private-f3113-hellopackageapi.apiary-mock.com")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .client(OkHttpClient.Builder().build())
        .build()

    private suspend fun getRoom(roomRequest: RoomRequest) =
        retrofit.create(Api::class.java).getRoomAsync(roomRequest)

    private fun loadingVisibility(visible: Boolean) {
        if (visible) {
            pb_loading.visibility = View.VISIBLE
            bt_call.visibility = View.GONE
        } else {
            pb_loading.visibility = View.GONE
            bt_call.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            showError(data?.extras?.getString(EXTRA_NAME, "") ?: "")
        }
    }

    companion object {
        const val REQUEST_CODE = 1234
        const val EXTRA_NAME = "EXTRA_NAME"
        const val USER_NAME = "Kiosk N1"
        const val HOST_URL = "prod.vidyo.io"
    }
}

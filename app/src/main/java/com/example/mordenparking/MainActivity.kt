package com.example.mordenparking

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.google.gson.*
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var functions: FirebaseFunctions
    private var imageUri: Uri? = null
    private val PICK_IMAGE_CODE = 100
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = Firebase.auth
        auth.signInWithEmailAndPassword("manhmap18112002@gmail.com", "manhabc2002")
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("ngoollll", "signInWithEmail:success")
                    val user = auth.currentUser
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("ngoollll", "signInWithEmail:failure", task.exception)
                }
            }


        findViewById<Button>(R.id.buttonLoadPicture).setOnClickListener {
            if(imageUri == null){
                val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                startActivityForResult(gallery, PICK_IMAGE_CODE)
            }
            else {
                doJobs()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE_CODE) {
            imageUri = data?.data
            findViewById<ImageView>(R.id.imageView).setImageURI(imageUri)
        }
    }

    private fun doJobs(){
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        bitmap = scaleBitmapDown(bitmap, 640)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        val base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        functions = Firebase.functions
        // Create json request to cloud vision
        val request = JsonObject()
        // Add image to request
        val image = JsonObject()
        image.add("content", JsonPrimitive(base64encoded))
        request.add("image", image)
        // Add features to the request
        val feature = JsonObject()
        feature.add("type", JsonPrimitive("TEXT_DETECTION"))
        feature.add("maxResults", JsonPrimitive(10))
        // Alternatively, for DOCUMENT_TEXT_DETECTION:
        // feature.add("type", JsonPrimitive("DOCUMENT_TEXT_DETECTION"))
        val features = JsonArray()
        features.add(feature)
        request.add("features", features)
        annotateImage(request.toString())
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val e = task.exception
                    if (e is FirebaseFunctionsException) {
                        val code = e.code
                        val details = e.details
                        Log.d("NGOLLLL",code.toString())
                        Log.d("NGOLLLL",details.toString())
                    }
                    Log.d("NGOLLLL",task.exception.toString())
                } else {
                    val annotation =
                        task.result!!.asJsonArray[0].asJsonObject["fullTextAnnotation"].asJsonObject
                    Log.d("NGOLLLL","%nComplete annotation:")
                    Log.d("NGOLLLL", annotation["text"].asString)
                    for (page in annotation["pages"].asJsonArray) {
                        var pageText = ""
                        for (block in page.asJsonObject["blocks"].asJsonArray) {
                            var blockText = ""
                            for (para in block.asJsonObject["paragraphs"].asJsonArray) {
                                var paraText = ""
                                for (word in para.asJsonObject["words"].asJsonArray) {
                                    var wordText = ""
                                    for (symbol in word.asJsonObject["symbols"].asJsonArray) {
                                        wordText += symbol.asJsonObject["text"].asString
                                        System.out.format(
                                            "Symbol text: %s (confidence: %f)%n",
                                            symbol.asJsonObject["text"].asString,
                                            symbol.asJsonObject["confidence"].asFloat,
                                        )
                                    }
                                    System.out.format(
                                        "Word text: %s (confidence: %f)%n%n",
                                        wordText,
                                        word.asJsonObject["confidence"].asFloat,
                                    )
                                    System.out.format(
                                        "Word bounding box: %s%n",
                                        word.asJsonObject["boundingBox"]
                                    )
                                    paraText = String.format("%s%s ", paraText, wordText)
                                }
                                System.out.format("%nParagraph: %n%s%n", paraText)
                                System.out.format(
                                    "Paragraph bounding box: %s%n",
                                    para.asJsonObject["boundingBox"]
                                )
                                System.out.format(
                                    "Paragraph Confidence: %f%n",
                                    para.asJsonObject["confidence"].asFloat
                                )
                                blockText += paraText
                            }
                            pageText += blockText
                        }
                    }
                }
            }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension
        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth =
                (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight =
                (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    private fun annotateImage(requestJson: String): Task<JsonElement> {
        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data
                Log.d("BUGGG",result.toString())
                JsonParser.parseString(Gson().toJson(result))
            }
    }
}
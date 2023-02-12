package com.example.driveproject

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.*


class UploadActivity : AppCompatActivity() {
    private var mDrive: Drive? = null
    var PICK_PHOTO_FOR_AVATAR = 123
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        var upload: Button = findViewById(R.id.gUpload)
        var signout:Button = findViewById(R.id.signout)
        var download:Button = findViewById(R.id.download)
        mDrive = getDriveService()
        upload.setOnClickListener {
            uploadFileToGDrive()
        }
        download.setOnClickListener {
            
        }
        signout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this,MainActivity::class.java))
            finish()
        }
    }

    fun getDriveService(): Drive? {
        GoogleSignIn.getLastSignedInAccount(this)?.let { googleAccount ->
            val credential = GoogleAccountCredential.usingOAuth2(
                this, listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = googleAccount.account!!
            return Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(getString(R.string.app_name))
                .build()
        }
        return null
    }

//for picking in android 11 and +
    val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            upload(uri)
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    //common code for both the version

    fun uploadFileToGDrive() {
        try {
            getDriveService()?.let { googleDriveService ->
                GlobalScope.launch {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Code for Android 11 and above
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        } else {
                            // Code for Android versions below 11
                            val pickImageIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                            }
                            startActivityForResult(pickImageIntent, PICK_PHOTO_FOR_AVATAR)
                        }

                    } catch (userAuthEx: UserRecoverableAuthIOException) {
                        startActivity(
                            userAuthEx.intent
                        )
                        Toast.makeText(applicationContext, "Authorized now you can upload...", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: Log.d("fd", "Signin error - not logged in")
        } catch (e: Exception) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PHOTO_FOR_AVATAR && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            if (imageUri != null) {
                upload(imageUri)
            }
            // Handle the selected image here
        }
    }
    fun upload(uri:Uri){
        val contentResolver = this.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        val file = File(this.cacheDir, "my_file_name")
        if (bytes != null) {
            file.writeBytes(bytes)
        }
        val gfile = com.google.api.services.drive.model.File()

        //Target-1
        /*this is for folder upload ...
          gfile.name = "newfolder"
          gfile.mimeType = "application/vnd.google-apps.folder"
           */

        //this is the process for the upload inside the folder
        gfile.name = file.name

        //here "1yb75593oBph9uc6Tg0yBiqrVV31pkh4P" is the id of specific file eg.https://drive.google.com/drive/u/4/folders/1yb75593oBph9uc6Tg0yBiqrVV31pkh4P
        //so we can create a file and return the id using the Target-1 Code (aisa hi naam de diya hai upar wala commented code hai)
        gfile.setParents(Collections.singletonList("1yb75593oBph9uc6Tg0yBiqrVV31pkh4P"))
        val mimetype = "image/jpeg"
        val fileContent = FileContent(mimetype, file)

        val progressbar = findViewById<ProgressBar>(R.id.progressbar)

        try {
            GlobalScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        progressbar.visibility = View.VISIBLE
                        withContext(Dispatchers.IO) {
                            launch {
                                /*
                                use this for folder upload
                                getDriveService()?.Files()?.create(gfile)?.setFields("id")?.execute()
                                 */
                                getDriveService()?.Files()?.create(gfile,fileContent)?.setFields("id, parents")?.execute()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle the exception here
                    if (e is UserRecoverableAuthIOException) {
                        startActivity(e.intent)
                    } else {
                        Log.e("TAG", "Exception during file upload", e)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        progressbar.visibility = View.GONE

                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Exception while launching coroutine", e)
        }


        fun downloadFile(fileId:String):ByteArrayOutputStream{
            val outputStream: OutputStream = ByteArrayOutputStream()
            try {
                getDriveService()?.files()?.export(fileId, "application/pdf")
                    ?.executeMediaAndDownloadTo(outputStream);
                return outputStream as ByteArrayOutputStream
            } catch (e:GoogleJsonResponseException ) {
                System.err.println("Unable to export file: " + e.getDetails());
                throw e;
            }
        }
        }
    }
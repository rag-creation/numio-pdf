package com.rr.numio.pdf

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.rr.numio.pdf.ui.PdfApp
import com.rr.numio.pdf.ui.theme.PdfReaderTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // Android's own file picker: the person chooses one PDF, and only that file
    // is shared with the app. No storage permission needed.
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keepAccess(uri)
            vm.open(uri, fromOutside = false)
        }
    }

    // "Save a copy": Android's own save screen, the person picks where the new PDF goes
    private val saveCopy = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            keepAccess(uri)
            vm.save(uri)
        }
    }

    // Organise pages: another PDF whose pages are added at the end
    private val addPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addToOrganise(uri)
    }

    // Organise pages: the rearranged PDF saved as a new file
    private val savePagesCopy = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            keepAccess(uri)
            vm.saveOrganised(uri)
        }
    }

    // Organise pages: the picked pages saved as a new PDF
    private val extractTo = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.extractPages(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)

        setContent {
            // While the app lock is on, the recent-apps screen shows no picture of the PDF
            LaunchedEffect(vm.appLock) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!vm.appLock)
            }
            PdfReaderTheme(theme = vm.appTheme) {
                PdfApp(
                    vm = vm,
                    onOpenPicker = ::pick,
                    onShare = ::share,
                    onSaveCopy = ::askWhereToSave,
                    onAddPdf = { launchSafely { addPdf.launch(arrayOf("application/pdf")) } },
                    onSavePagesCopy = { name -> launchSafely { savePagesCopy.launch("${baseName(name)} (pages).pdf") } },
                    onExtract = { name -> launchSafely { extractTo.launch("${baseName(name)} (extract).pdf") } },
                    onLeave = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.cameBack(System.currentTimeMillis())
    }

    override fun onStop() {
        super.onStop()
        // Turning the phone round isn't leaving
        if (!isChangingConfigurations) vm.left(System.currentTimeMillis())
    }

    /** Every picker, share sheet and link goes through here: leaving on purpose. */
    @Deprecated("Overridden only to notice the app sending the person elsewhere")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        vm.outing = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun pick() {
        try {
            picker.launch(arrayOf("application/pdf"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This phone has no file picker to choose a PDF with.", Toast.LENGTH_LONG).show()
        }
    }

    /** A PDF tapped or shared in another app. */
    private fun handle(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        keepAccess(uri)
        vm.open(uri, fromOutside = true)
    }

    private fun baseName(name: String) = name.removeSuffix(".pdf").removeSuffix(".PDF")

    private fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This phone has no screen for choosing files.", Toast.LENGTH_LONG).show()
        }
    }

    private fun askWhereToSave(name: String) {
        val base = name.removeSuffix(".pdf").removeSuffix(".PDF")
        try {
            saveCopy.launch("$base (marked).pdf")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This phone has no screen for saving files.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Asks Android to let the app reopen this file later from the library.
     * Some apps only lend a file for one visit; then this quietly does nothing.
     */
    private fun keepAccess(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        // Read and write if offered (so marks can be saved into the file itself),
        // otherwise read only
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: SecurityException) {
                // Not offered by the app that owns the file
            }
        }
    }

    private fun share(uri: String, name: String) {
        val u = Uri.parse(uri)
        if (u.scheme != ContentResolver.SCHEME_CONTENT) {
            Toast.makeText(this, "This file can't be shared from here.", Toast.LENGTH_LONG).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, u)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(send, null))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't share this file.", Toast.LENGTH_LONG).show()
        }
    }
}

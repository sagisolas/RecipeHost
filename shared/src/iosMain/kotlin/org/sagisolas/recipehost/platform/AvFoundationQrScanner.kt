/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.cancel
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.presentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.objc.sel_registerName
import kotlin.coroutines.resume

/**
 * [QrScanner] on AVFoundation: a camera preview in a modal sheet, closed by the first QR code it
 * reads, its Cancel button or a swipe down. Throws [CameraAccessDenied] when camera access is
 * denied or restricted, and [IllegalStateException] when there is no usable camera.
 */
internal class AvFoundationQrScanner : QrScanner {
    override suspend fun scan(): String? =
        withContext(Dispatchers.Main) {
            requireCameraAccess()
            val (session, output) = qrCaptureSession()
            val cancelTitle = getString(Res.string.cancel)
            suspendCancellableCoroutine { continuation ->
                val scanner =
                    QrScannerViewController(session, output, cancelTitle) { code ->
                        if (continuation.isActive) continuation.resume(code)
                    }
                continuation.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) { scanner.close() } }
                topViewController().presentViewController(scanner, animated = true) { scanner.onPresented() }
            }
        }
}

/** Asks for camera access if it hasn't been decided; throws [CameraAccessDenied] if it's not granted. */
private suspend fun requireCameraAccess() {
    val granted =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                true
            }

            AVAuthorizationStatusNotDetermined -> {
                suspendCancellableCoroutine { continuation ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        continuation.resume(granted)
                    }
                }
            }

            else -> {
                false
            }
        }
    if (!granted) throw CameraAccessDenied()
}

/** A session from the default camera to a metadata output limited to QR codes. */
@OptIn(ExperimentalForeignApi::class)
private fun qrCaptureSession(): Pair<AVCaptureSession, AVCaptureMetadataOutput> {
    val camera = checkNotNull(AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)) { "No camera" }
    val input = checkNotNull(AVCaptureDeviceInput.deviceInputWithDevice(camera, error = null)) { "No camera input" }
    val output = AVCaptureMetadataOutput()
    val session = AVCaptureSession()
    check(session.canAddInput(input) && session.canAddOutput(output)) { "Camera can't read QR codes" }
    session.addInput(input)
    session.addOutput(output)
    // Only valid once the output is in the session.
    output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
    return session to output
}

/**
 * The view controller that presentations start from: the key window's root, then whatever it
 * presents, short of one being dismissed (a scanner sheet still closing, when scanning again).
 */
private fun topViewController(): UIViewController {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .first { it.isKeyWindow() }
    var top = checkNotNull(window.rootViewController)
    while (true) top = top.presentedViewController?.takeUnless { it.isBeingDismissed() } ?: return top
}

/**
 * The scanner sheet: [session]'s preview filling the view, and a Cancel button. Calls [onResult]
 * once, with the first code read, or `null` on Cancel or a swipe down. The session starts and
 * stops on its own serial queue, as AVFoundation requires.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QrScannerViewController(
    private val session: AVCaptureSession,
    private val output: AVCaptureMetadataOutput,
    private val cancelTitle: String,
    private val onResult: (String?) -> Unit,
) : UIViewController(nibName = null, bundle = null),
    AVCaptureMetadataOutputObjectsDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {
    private val sessionQueue = dispatch_queue_create("org.sagisolas.recipehost.qr-scanner", null)
    private val preview = AVCaptureVideoPreviewLayer(session = session)
    private var finished = false

    /** The present animation has ended; a dismiss before then is ignored, so [onPresented] does it. */
    private var presented = false

    init {
        output.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        // Through the superclass: this class's delegate methods shadow the property's name.
        (this as UIViewController).presentationController?.delegate = this
        view.backgroundColor = UIColor.blackColor
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(preview)
        addCancelButton()
        dispatch_async(sessionQueue) { session.startRunning() }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
    }

    private fun addCancelButton() {
        val button = UIButton.buttonWithType(UIButtonTypeSystem)
        button.setTitle(cancelTitle, forState = UIControlStateNormal)
        button.tintColor = UIColor.whiteColor
        button.addTarget(this, sel_registerName("cancelTapped"), UIControlEventTouchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(button)
        button.centerXAnchor.constraintEqualToAnchor(view.centerXAnchor).active = true
        button.bottomAnchor
            .constraintEqualToAnchor(view.safeAreaLayoutGuide.bottomAnchor, constant = -CANCEL_BOTTOM_INSET)
            .active = true
    }

    @ObjCAction
    fun cancelTapped() = finish(null, dismiss = true)

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val code =
            didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>().firstNotNullOfOrNull {
                it.stringValue
            }
        if (code != null) finish(code, dismiss = true)
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) =
        finish(null, dismiss = false)

    /** The present animation has ended: closes the sheet now if the scan ended meanwhile. */
    fun onPresented() {
        presented = true
        if (finished) dismissViewControllerAnimated(true, completion = null)
    }

    /** Closes the sheet without a result, when the scan's coroutine is cancelled. Main thread only. */
    fun close() {
        end(dismiss = true)
    }

    private fun finish(
        code: String?,
        dismiss: Boolean,
    ) {
        if (end(dismiss)) onResult(code)
    }

    /** Stops the camera and, if [dismiss], closes the sheet; false if it had already ended. */
    private fun end(dismiss: Boolean): Boolean {
        if (finished) return false
        finished = true
        // Drops the output's reference back to this controller.
        output.setMetadataObjectsDelegate(null, queue = null)
        dispatch_async(sessionQueue) { session.stopRunning() }
        if (dismiss && presented) dismissViewControllerAnimated(true, completion = null)
        return true
    }
}

/** Points between the Cancel button and the bottom of the safe area. */
private const val CANCEL_BOTTOM_INSET = 24.0

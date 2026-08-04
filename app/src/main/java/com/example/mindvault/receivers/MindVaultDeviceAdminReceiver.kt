package com.example.mindvault.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Administrator receiver that prevents app uninstallation.
 * When active, the user must first deactivate this admin before they
 * can uninstall MindVault — and the Accessibility Service guards
 * the deactivation settings page.
 */
class MindVaultDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MindVaultDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled — uninstall protection active")
        Toast.makeText(context, "MindVault: Uninstall protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device Admin disable requested")
        return "Disabling will remove uninstall protection. MindVault won't be able to protect itself from being removed."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled — uninstall protection removed")
        Toast.makeText(context, "MindVault: Uninstall protection removed", Toast.LENGTH_SHORT).show()
    }
}

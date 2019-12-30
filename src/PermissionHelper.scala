package org.aprsdroid.app

import android.app.{Activity, AlertDialog}
import android.content.{DialogInterface, Intent}
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

trait PermissionHelper extends Activity {
	def getActionName(action : Int): Int
	def onAllPermissionsGranted(action : Int): Unit

	def checkPermissions(permissions : Array[String], action : Int): Boolean = {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			onAllPermissionsGranted(action)
			true
		} else {
			import android.Manifest
			var need_dialog = false
			for (p <- permissions)
				need_dialog |= (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
			if (need_dialog) {
				//if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
				//	Toast.makeText(this, "No permission!", Toast.LENGTH_SHORT).show()
				requestPermissions(permissions, action)
				false
			} else {
				onAllPermissionsGranted(action)
				true
			}
		}
	}

	abstract override def onRequestPermissionsResult(requestCode: Int, permissions: Array[_root_.java.lang.String], grantResults: Array[Int]): Unit = {
		val failed_perms = scala.collection.mutable.Set[String]()
		for (i <- permissions.indices) {
			if (grantResults(i) != PackageManager.PERMISSION_GRANTED) {
				failed_perms.add(permissions(i))
			}
		}
		if (failed_perms.size > 0) {
			onPermissionsFailed(requestCode, failed_perms.toSet)
		} else {
			onAllPermissionsGranted(requestCode)
		}
	}

	def getPermissionName(permission : String) : String = {
		try {
			val pi = getPackageManager.getPermissionInfo(permission, 0)
				pi.loadLabel(getPackageManager()).toString()
		} catch {
			case e : PackageManager.NameNotFoundException => permission.split("\\.").last
		}
	}
	def onPermissionsFailed(action : Int, permissions : Set[String]): Unit = {
		val sb = new StringBuilder(getString(R.string.no_perm_text))
		sb.append("\n\n")
		for (p <- permissions) {
			sb.append("- ").append(getPermissionName(p)).append("\n")
		}
		new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(getActionName(action))
			.setMessage(sb.toString())
			.setPositiveButton(R.string.preferences, new DialogInterface.OnClickListener {
				override def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
					val intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
						intent.setData(Uri.fromParts("package", getPackageName(), null))
					startActivity(intent)
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.create().show()
	}

}

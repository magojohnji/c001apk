package com.example.c001apk.ui.fragment.home.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.AppItem
import com.example.c001apk.logic.model.UpdateCheckResponse
import com.example.c001apk.logic.network.Repository.getAppsUpdate
import com.example.c001apk.util.Utils
import com.example.c001apk.util.Utils.getBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.core.content.pm.longVersionCodeCompat

class AppListViewModel : ViewModel() {


    var isInit: Boolean = true
    var listSize: Int = -1
    var firstVisibleItemPosition = 0

    val setFab: MutableLiveData<Boolean> = MutableLiveData()
    val items: MutableLiveData<List<AppItem>> = MutableLiveData()

    val appsUpdate = ArrayList<UpdateCheckResponse.Data>()

    private fun fetchAppsUpdate(pkg:String) {
        viewModelScope.launch {
            getAppsUpdate(pkg)
                .collect{
                    it.getOrNull()?.let {
                        appsUpdate.addAll(it)
                        setFab.postValue(true)
                    }
                }
        }

    }

    fun getItems(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appList = context.packageManager
                .getInstalledApplications(PackageManager.GET_SHARED_LIBRARY_FILES)
            val newItems = ArrayList<AppItem>()
            val updateCheckJsonObject = JSONObject()

            for (info in appList) {
                if (((info.flags and ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM)) {
                    val packageInfo = context.packageManager.getPackageInfo(info.packageName, 0)

                    val appItem = AppItem().apply {
                        packageName = info.packageName
                        versionName =
                            "${packageInfo.versionName}(${packageInfo.longVersionCodeCompat})"
                        lastUpdateTime = packageInfo.lastUpdateTime
                    }

                    if (appItem.packageName != "com.example.c001apk")
                        newItems.add(appItem)

                    if (info.packageName != "com.example.c001apk")
                        updateCheckJsonObject.put(
                            info.packageName,
                            "0,${packageInfo.longVersionCodeCompat},${Utils.getInstalledAppMd5(info)}"
                        )
                }
            }

            withContext(Dispatchers.Main) {
                items.value =
                    newItems.sortedByDescending { it.lastUpdateTime }.toCollection(ArrayList())
                fetchAppsUpdate(updateCheckJsonObject.toString().getBase64(false))
            }
        }
    }

}
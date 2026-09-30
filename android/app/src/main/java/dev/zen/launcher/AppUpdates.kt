package dev.zen.launcher

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class UpdateFileProvider:FileProvider()

internal data class UpdatePackageIdentity(val packageName:String,val versionCode:Long,val signers:Set<String>)
internal object UpdatePackageVerifier {
    private fun identity(info:PackageInfo)=UpdatePackageIdentity(info.packageName,info.longVersionCode,
        info.signingInfo?.apkContentsSigners.orEmpty().map{UpdateProtocol.fingerprint(it.toByteArray())}.toSet())
    fun installed(context:Context)=identity(context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES))
    fun archive(context:Context,file:File):UpdatePackageIdentity = identity(
        context.packageManager.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES)
            ?:throw IllegalArgumentException(tr(R.string.ui_2b6d9c86fa26, "文件不是可识别的 Android 安装包。")))
    fun verify(context:Context,file:File,release:UpdateManifest,hash:String) {
        val archive=archive(context,file);val current=installed(context)
        UpdateProtocol.verifyArtifact(release,archive.packageName,archive.versionCode,current.versionCode,archive.signers,current.signers,hash)
    }
}

data class UpdateUiState(
    val address:String="",val loadingAddress:Boolean=true,val busy:Boolean=false,val phase:String="idle",
    val release:UpdateManifest?=null,val bytes:Long=0,val totalBytes:Long?=null,val message:String="",
    val automatic:Boolean=true
) {
    val canDownload:Boolean get()=!busy&&release!=null&&phase!="ready"
    val canInstall:Boolean get()=!busy&&phase=="ready"&&release!=null
}
private data class VerifiedUpdate(val release:UpdateManifest,val file:File)

/** Small, throttled manifest checks on home resume; downloads/installation always require a tap. */
class AppUpdates(private val activity:Activity,
                 private val manifestReader:(suspend (String)->ByteArray)?=null,
                 private val clock:()->Long=System::currentTimeMillis) {
    private val context=activity.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val generation=AtomicInteger(0)
    private val connection=AtomicReference<HttpURLConnection?>(null)
    private val verified=AtomicReference<VerifiedUpdate?>(null)
    private var job:Job?=null
    private var foregroundJob:Job?=null
    private val initialization:Job
    @Volatile private var lastAttempt=0L
    private val mutable=MutableStateFlow(UpdateUiState(address=BuildConfig.UPDATE_MANIFEST_URL.trim()))
    val state=mutable.asStateFlow()
    private val directory get()=File(context.cacheDir,"updates")
    private val preferences get()=context.getSharedPreferences("app-updates",Context.MODE_PRIVATE)

    init {
        initialization=scope.launch {
            val address=preferences.getString("manifest-url",BuildConfig.UPDATE_MANIFEST_URL).orEmpty().trim()
            lastAttempt=preferences.getLong("last-attempt",0L)
            val cached=if(address.isBlank())null else runCatching{
                UpdateProtocol.parseManifest(preferences.getString("cached-manifest","").orEmpty().toByteArray())
                    .takeIf{it.versionCode>UpdatePackageVerifier.installed(context).versionCode}
            }.getOrNull()
            mutable.update{it.copy(address=address,loadingAddress=false,automatic=preferences.getBoolean("automatic-check",true),
                release=cached,phase=if(cached!=null)"available"else"idle",
                message=if(address.isEmpty())tr(R.string.ui_62fe85771415, "尚未配置更新地址。")else if(cached!=null)tr(R.string.ui_5ea990884722,"有新版本可用。")else"")}
        }
    }
    fun resume() {
        foregroundJob?.cancel()
        foregroundJob=scope.launch(Dispatchers.Main) {
            initialization.join()
            // Do not compete with the first home frame or rapid app switching.
            delay(1500)
            check(automatic=true)
        }
    }
    fun pause(){foregroundJob?.cancel();foregroundJob=null}
    fun setAutomatic(enabled:Boolean) {
        if(state.value.busy||state.value.loadingAddress)return
        val token=begin("saving",keepVerified=true)
        val phase=if(verified.get()!=null)"ready"else if(state.value.release!=null)"available"else"idle"
        job=scope.launch {
            val saved=preferences.edit().putBoolean("automatic-check",enabled).commit()
            publish(token){it.copy(automatic=if(saved)enabled else it.automatic,busy=false,phase=phase,
                message=if(saved)""else tr(R.string.update_save_failed,"设置未能保存，请重试。"))}
        }
    }
    fun saveAddress(value:String) {
        if(state.value.busy||state.value.loadingAddress)return
        val address=value.trim()
        try{if(address.isNotEmpty())UpdateProtocol.httpsAddress(address)}catch(e:IllegalArgumentException){
            mutable.update{it.copy(message=e.message?:tr(R.string.ui_400b7a213a80, "更新地址无效。"))};return
        }
        val token=begin("saving",clearRelease=true)
        job=scope.launch {
            val saved=preferences.edit().putString("manifest-url",address).remove("cached-manifest").remove("last-attempt").commit()
            if(saved)lastAttempt=0L
            publish(token){it.copy(address=if(saved)address else it.address,busy=false,phase="idle",
                message=if(!saved)tr(R.string.ui_e26edd055429, "地址未能保存，请重试。")else if(address.isEmpty())tr(R.string.ui_62fe85771415, "尚未配置更新地址。")else tr(R.string.ui_e7f31dca2d47, "更新地址已保存。"))}
        }
    }
    fun check(automatic:Boolean=false) {
        if(state.value.busy||state.value.loadingAddress)return
        val address=state.value.address
        val now=clock()
        if(automatic&&(!AutomaticUpdatePolicy.due(state.value.automatic,address,now,lastAttempt)||state.value.canInstall))return
        if(address.isBlank()){mutable.update{it.copy(message=tr(R.string.ui_5e6927df58b7, "尚未配置更新地址。展开下方设置后填写 HTTPS 地址。"))};return}
        lastAttempt=now
        val token=begin("checking")
        job=scope.launch {
            try {
                // Record failed attempts too: offline returns must not flood the network.
                check(preferences.edit().putLong("last-attempt",now).commit()) { tr(R.string.update_save_failed,"设置未能保存，请重试。") }
                val bytes=manifestReader?.invoke(address)?:request(address,UpdateProtocol.MANIFEST_LIMIT){stream,_->
                    ByteArrayOutputStream().use{output->
                        UpdateProtocol.copyLimited(stream,output,UpdateProtocol.MANIFEST_LIMIT,checkCancelled={ensureActive()})
                        output.toByteArray()
                    }
                }
                val release=UpdateProtocol.parseManifest(bytes)
                val newer=release.versionCode>UpdatePackageVerifier.installed(context).versionCode
                ensureActive()
                if(generation.get()!=token)return@launch
                preferences.edit().putString("cached-manifest",bytes.toString(Charsets.UTF_8)).commit()
                publish(token){it.copy(busy=false,phase=if(newer)"available"else"current",release=if(newer)release else null,
                    message=if(newer)tr(R.string.ui_5ea990884722, "有新版本可用。")else tr(R.string.ui_ca9955f4ec2a, "当前已是最新版本。"))}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){fail(token,e)}
        }
    }
    fun download() {
        val release=state.value.release?:return
        if(!state.value.canDownload)return
        val token=begin("downloading")
        job=scope.launch {
            var temporary:File?=null
            try {
                UpdateProtocol.validate(release)
                require(release.versionCode>UpdatePackageVerifier.installed(context).versionCode){tr(R.string.ui_3128976f85a2, "当前已安装此版本或更新版本。")}
                check(directory.exists()||directory.mkdirs()){tr(R.string.ui_17ccf7e0efcd, "无法创建下载目录。")}
                val tempFile=File(directory,"${UUID.randomUUID()}.part")
                temporary=tempFile
                val copy=request(release.apkUrl,UpdateProtocol.APK_LIMIT){stream,length->
                    if(release.sizeBytes!=null&&length>=0)require(length==release.sizeBytes){tr(R.string.ui_21f2e606a2a6, "下载大小与更新说明不符。")}
                    val total=release.sizeBytes?:length.takeIf{it>0}
                    publish(token){it.copy(totalBytes=total)}
                    var lastProgress=0L
                    tempFile.outputStream().buffered().use{output->
                        UpdateProtocol.copyLimited(stream,output,UpdateProtocol.APK_LIMIT,release.sizeBytes,
                            checkCancelled={ensureActive()},progress={bytes->
                                if(bytes-lastProgress>=256*1024){lastProgress=bytes;publish(token){it.copy(bytes=bytes)}}
                            })
                    }
                }
                ensureActive()
                UpdatePackageVerifier.verify(context,tempFile,release,copy.sha256)
                val target=File(directory,"zen-${release.versionCode}-${release.sha256.take(16)}.apk")
                if(target.exists())check(target.delete()){tr(R.string.ui_9017006d2003, "旧下载文件无法替换。")}
                check(tempFile.renameTo(target)){tr(R.string.ui_baf58a21d52e, "安装包无法保存，请重新下载。")}
                temporary=null
                ensureActive()
                if(generation.get()==token) {
                    verified.set(VerifiedUpdate(release,target))
                    publish(token){it.copy(busy=false,phase="ready",bytes=copy.bytes,totalBytes=copy.bytes,message=tr(R.string.ui_04988b4608b1, "安装包已验证，准备安装。"))}
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){fail(token,e)}
            finally{temporary?.delete()}
        }
    }
    fun install() {
        val ready=verified.get()?:run{mutable.update{it.copy(message=tr(R.string.ui_babfea9be8bf, "请先下载并验证安装包。"))};return}
        if(!state.value.canInstall)return
        val token=begin("verifying",keepVerified=true)
        job=scope.launch {
            try {
                val file=ready.file
                require(file.isFile&&file.canonicalFile.parentFile==directory.canonicalFile){tr(R.string.ui_d9684e00b295, "下载文件已失效，请重新下载。")}
                val sink=object:OutputStream(){override fun write(value:Int){};override fun write(bytes:ByteArray,offset:Int,length:Int){}}
                val digest=file.inputStream().buffered().use{input->
                    UpdateProtocol.copyLimited(input,sink,UpdateProtocol.APK_LIMIT,ready.release.sizeBytes,checkCancelled={ensureActive()})
                }
                UpdatePackageVerifier.verify(context,file,ready.release,digest.sha256)
                ensureActive()
                withContext(Dispatchers.Main) {
                    if(generation.get()!=token||activity.isFinishing||activity.isDestroyed)return@withContext
                    publish(token){it.copy(busy=false,phase="ready")}
                    if(!activity.packageManager.canRequestPackageInstalls()) {
                        mutable.update{it.copy(message=tr(R.string.ui_111690cefb0c, "允许安装此来源后，请返回并再次点“安装更新”。"))}
                        val intent=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))
                        runCatching{activity.startActivity(intent)}.onFailure{
                            runCatching{activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
                                .onFailure{mutable.update{it.copy(message=tr(R.string.ui_de512735d845, "系统未提供安装权限入口，请在应用设置中检查。"))}}
                        }
                        return@withContext
                    }
                    val uri=FileProvider.getUriForFile(activity,"${context.packageName}.updates",file)
                    val intent=Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData=ClipData.newRawUri(tr(R.string.ui_dcd68487e2f0, "禅更新"),uri)
                    activity.startActivity(intent)
                    mutable.update{it.copy(message=tr(R.string.ui_9544db0fa85a, "请在系统安装界面中确认。"))}
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation.get()==token)verified.set(null);fail(token,e)}
        }
    }
    fun cancel() {
        if(!state.value.busy)return
        generation.incrementAndGet();stopConnection();job?.cancel()
        mutable.update{it.copy(busy=false,phase=if(verified.get()!=null)"ready"else if(it.release!=null)"available"else"idle",message=tr(R.string.ui_2ad07a03c6df, "已取消。"))}
    }
    fun close(){pause();generation.incrementAndGet();job?.cancel();stopConnection();scope.cancel()}

    private fun begin(phase:String,clearRelease:Boolean=false,keepVerified:Boolean=false):Int {
        val token=generation.incrementAndGet()
        job?.cancel();stopConnection()
        if(!keepVerified)verified.set(null)
        mutable.update{it.copy(busy=true,phase=phase,release=if(clearRelease)null else it.release,bytes=0,totalBytes=null,message="")}
        return token
    }
    private fun publish(token:Int,change:(UpdateUiState)->UpdateUiState){if(generation.get()==token)mutable.update(change)}
    private fun fail(token:Int,e:Exception){publish(token){it.copy(busy=false,phase="error",message=if(e is IllegalArgumentException||e is IllegalStateException)e.message?:tr(R.string.ui_70694bdabef2, "更新未完成。")else tr(R.string.ui_5adb4d9436b1, "暂时无法完成更新，请检查网络后重试。"))}}
    private fun stopConnection(){connection.getAndSet(null)?.let{current->scope.launch(NonCancellable){runCatching{current.disconnect()}}}}
    private suspend fun <T> request(address:String,limit:Long,read:(java.io.InputStream,Long)->T):T {
        var uri=UpdateProtocol.httpsAddress(address);var redirects=0
        while(true) {
            currentCoroutineContext().ensureActive()
            val current=uri.toURL().openConnection() as HttpURLConnection
            current.instanceFollowRedirects=false;current.connectTimeout=15_000;current.readTimeout=20_000
            current.setRequestProperty("Accept-Encoding","identity")
            current.setRequestProperty("User-Agent","Zen-Android/${BuildConfig.VERSION_NAME}")
            connection.set(current)
            try {
                val code=current.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    uri=UpdateProtocol.redirect(uri,current.getHeaderField("Location"),++redirects)
                    continue
                }
                require(code==200){tr(R.string.ui_291d02134ca1, "更新服务暂不可用（%1\$s）。", code)}
                val length=current.contentLengthLong
                UpdateProtocol.checkDeclaredSize(length,limit)
                return current.inputStream.use{read(it,length)}
            }finally{connection.compareAndSet(current,null);current.disconnect()}
        }
    }
}

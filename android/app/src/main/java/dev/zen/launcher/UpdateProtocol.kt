package dev.zen.launcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest

@Serializable data class UpdateManifest(
    val packageName:String,val versionCode:Long,val versionName:String,val apkUrl:String,val sha256:String,
    val sizeBytes:Long?=null,val notes:String=""
)
data class UpdateCopyResult(val bytes:Long,val sha256:String)

object UpdateProtocol {
    const val PACKAGE_NAME="dev.zen.launcher"
    const val MANIFEST_LIMIT=64L*1024
    const val APK_LIMIT=120L*1024*1024
    const val MAX_REDIRECTS=5
    private val json=Json{ignoreUnknownKeys=true}
    fun httpsAddress(value:String):URI {
        val uri=try{URI(value.trim())}catch(_:Exception){throw IllegalArgumentException(tr(R.string.ui_0f0b375cd7cb, "请输入有效的 HTTPS 地址。"))}
        require(uri.scheme.equals("https",true)&&!uri.host.isNullOrBlank()&&uri.rawUserInfo==null&&uri.rawFragment==null&&
            (uri.port==-1||uri.port in 1..65535)){tr(R.string.ui_57d53ac1fc02, "更新地址必须为 HTTPS，且不能包含账号或片段。")}
        return uri
    }
    fun redirect(current:URI,location:String?,redirectCount:Int):URI {
        require(redirectCount in 1..MAX_REDIRECTS){tr(R.string.ui_d2843d6f15a8, "更新地址跳转过多。")}
        require(!location.isNullOrBlank()){ tr(R.string.ui_f2de598f933b, "更新地址跳转无效。") }
        return httpsAddress(current.resolve(location).toString())
    }
    fun parseManifest(bytes:ByteArray):UpdateManifest {
        require(bytes.size.toLong()<=MANIFEST_LIMIT){tr(R.string.ui_df9471cee4f2, "更新说明超过大小限制。")}
        val release=try{json.decodeFromString<UpdateManifest>(bytes.toString(Charsets.UTF_8))}
            catch(_:Exception){throw IllegalArgumentException(tr(R.string.ui_1d21c0b5a40d, "更新说明格式不正确。"))}
        validate(release)
        return release.copy(sha256=release.sha256.lowercase())
    }
    fun validate(release:UpdateManifest) {
        require(release.packageName==PACKAGE_NAME){tr(R.string.ui_ec5160070648, "更新包不属于禅。")}
        require(release.versionCode>0&&release.versionName.isNotBlank()&&release.versionName.length<=80){tr(R.string.ui_602f482f67d0, "更新版本信息不正确。")}
        httpsAddress(release.apkUrl)
        require(release.sha256.matches(Regex("[0-9a-fA-F]{64}"))){tr(R.string.ui_b3b6bc23e253, "更新包缺少有效的校验信息。")}
        require(release.sizeBytes==null||release.sizeBytes in 1..APK_LIMIT){tr(R.string.ui_cb6a5ffb8b84, "更新包超过 120 MB 限制或大小无效。")}
        require(release.notes.length<=8000){tr(R.string.ui_3f82bd010cd2, "更新说明过长。")}
    }
    fun checkDeclaredSize(size:Long,limit:Long) {
        require(size<0||size<=limit){tr(R.string.ui_583546810e51, "下载内容超过大小限制。")}
    }
    fun copyLimited(input:InputStream,output:OutputStream,limit:Long,expected:Long?=null,
                    checkCancelled:()->Unit={},progress:(Long)->Unit={}):UpdateCopyResult {
        require(limit>0)
        if(expected!=null)require(expected in 1..limit){tr(R.string.ui_fe3ffb0c838b, "下载大小无效。")}
        val digest=MessageDigest.getInstance("SHA-256")
        val buffer=ByteArray(32*1024);var total=0L
        while(true) {
            checkCancelled()
            val count=input.read(buffer)
            if(count<0)break
            if(count==0)continue
            require(total<=limit-count){tr(R.string.ui_583546810e51, "下载内容超过大小限制。")}
            output.write(buffer,0,count);digest.update(buffer,0,count);total+=count
            progress(total)
        }
        checkCancelled()
        require(expected==null||expected==total){tr(R.string.ui_2f9ca88d6e90, "下载不完整，文件大小不符。")}
        return UpdateCopyResult(total,hex(digest.digest()))
    }
    fun verifyArtifact(release:UpdateManifest,actualPackage:String,actualVersion:Long,currentVersion:Long,
                       archiveSigners:Set<String>,installedSigners:Set<String>,actualHash:String) {
        validate(release)
        require(actualHash.equals(release.sha256,true)){tr(R.string.ui_85cebba9b8f4, "安装包校验不一致，请重新下载。")}
        require(actualPackage==PACKAGE_NAME){tr(R.string.ui_94028829096f, "安装包不属于禅。")}
        require(actualVersion==release.versionCode&&actualVersion>currentVersion){tr(R.string.ui_0714ff2f52b3, "安装包版本不符或不是更新版本。")}
        require(installedSigners.isNotEmpty()&&archiveSigners.isNotEmpty()&&archiveSigners==installedSigners){tr(R.string.ui_c5f06e800999, "安装包签名与当前版本不一致。")}
    }
    fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt()and 255)}
    fun fingerprint(bytes:ByteArray)=hex(MessageDigest.getInstance("SHA-256").digest(bytes))
}

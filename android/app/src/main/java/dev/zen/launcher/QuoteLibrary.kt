package dev.zen.launcher

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

@Serializable data class QuoteEdit(val scope:String,val id:String,val text:String,val custom:Boolean=false,val deleted:Boolean=false)
internal data class LibraryQuote(val id:String,val text:String,val credit:String,val source:String,val custom:Boolean=false,val edited:Boolean=false,val deleted:Boolean=false)

internal object QuoteLibrary {
    fun id(text:String):String {
        val hex="0123456789abcdef"
        return "builtin-"+buildString{MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).forEach{byte->
            val n=byte.toInt() and 255;append(hex[n ushr 4]);append(hex[n and 15])
        }}
    }
    fun rows(scope:String,builtins:List<ThemeQuote>,edits:List<QuoteEdit>,includeDeleted:Boolean=false):List<LibraryQuote> {
        val changes=edits.filter{it.scope==scope}.associateBy{it.id}
        val originals=builtins.distinctBy{it.text}.map{quote->
            val id=id(quote.text);val edit=changes[id]
            LibraryQuote(id,edit?.text?:quote.text,
                if(edit!=null&&edit.text!=quote.text)tr(R.string.ui_9daffbc8a4a6, "用户编辑 · 非原文")else quote.credit,
                if(edit!=null&&edit.text!=quote.text)""else quote.source,
                edited=edit!=null&&edit.text!=quote.text,deleted=edit?.deleted==true)
        }
        val custom=changes.values.filter{it.custom}.map{LibraryQuote(it.id,it.text,tr(R.string.ui_ba18069bd92f, "用户添加"),"",custom=true,deleted=it.deleted)}
        return (originals+custom).filter{includeDeleted||!it.deleted}
    }
    fun save(s:AppState,scope:String,text:String,row:LibraryQuote?=null):AppState {
        val value=text.trim();require(value.isNotEmpty()&&value.length<=160)
        val edit=QuoteEdit(scope,row?.id?:"custom-${UUID.randomUUID()}",value,row?.custom?:true)
        return s.copy(quoteEdits=s.quoteEdits.filterNot{it.scope==scope&&it.id==edit.id}+edit)
    }
    fun delete(s:AppState,scope:String,row:LibraryQuote)=s.copy(quoteEdits=s.quoteEdits.filterNot{it.scope==scope&&it.id==row.id}+
        QuoteEdit(scope,row.id,row.text,row.custom,deleted=true))
    fun restore(s:AppState,scope:String,row:LibraryQuote):AppState = if(row.custom)
        s.copy(quoteEdits=s.quoteEdits.map{if(it.scope==scope&&it.id==row.id)it.copy(deleted=false)else it})
        else s.copy(quoteEdits=s.quoteEdits.filterNot{it.scope==scope&&it.id==row.id})
}

package dev.zen.launcher

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.time.*
import kotlin.math.ceil

/** Keep the detail row and its highlight inside the period currently on screen. */
internal fun statisticsSelectionForPeriod(anchor:LocalDate,selected:LocalDate,today:LocalDate,monthly:Boolean):LocalDate {
    val days=DailyTools.periodDays(anchor,monthly)
    return when {
        selected in days -> selected
        today in days -> today
        else -> days.first()
    }
}

private val statisticsDateSaver=Saver<LocalDate,Long>(save={it.toEpochDay()},restore={LocalDate.ofEpochDay(it)})

@Composable fun FocusStatistics(a:MainActivity,s:AppState,now:Long){
    val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    var anchor by rememberSaveable(stateSaver=statisticsDateSaver){mutableStateOf(today)}
    var selected by rememberSaveable(stateSaver=statisticsDateSaver){mutableStateOf(today)}
    var monthly by rememberSaveable{mutableStateOf(false)};var calendar by rememberSaveable{mutableStateOf(false)}
    var explanation by rememberSaveable{mutableStateOf(false)}
    val days=DailyTools.periodDays(anchor,monthly)
    val total=DailyTools.total(s.daily,days);val c=MaterialTheme.colorScheme
    fun dateLabel(day:LocalDate)=if(day.year!=today.year||days.first().year!=days.last().year)"${day.year}.${day.monthValue}.${day.dayOfMonth}"else"${day.monthValue}.${day.dayOfMonth}"

    val next=if(monthly)anchor.withDayOfMonth(1).plusMonths(1)else anchor.plusWeeks(1)
    PanelColumn(tr(R.string.ui_305471d2e26d, "专注统计"),a,spacing=4.dp,pinned={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
            FilterChip(selected=!monthly,onClick={monthly=false;calendar=false;anchor=today;selected=today},label={Text(tr(R.string.ui_3816e03a7608, "按周"))},shape=RoundedCornerShape(22.dp))
            Spacer(Modifier.width(12.dp))
            FilterChip(selected=monthly,onClick={monthly=true;anchor=today;selected=today},label={Text(tr(R.string.ui_ca3c684d88c7, "按月"))},shape=RoundedCornerShape(22.dp))
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            IconButton(onClick={anchor=if(monthly)anchor.withDayOfMonth(1).minusMonths(1)else anchor.minusWeeks(1);selected=DailyTools.periodDays(anchor,monthly).first()},modifier=Modifier.semantics{contentDescription=if(monthly)tr(R.string.ui_692c48b73e2d, "上月") else tr(R.string.ui_0cf8b14f4651, "上周")}){LineIcon("back",c.secondary)}
            Text(if(monthly)tr(R.string.ui_815eb0065b43, "%1\$s 年 %2\$s 月", anchor.year, anchor.monthValue) else "${dateLabel(days.first())} — ${dateLabel(days.last())}",modifier=Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=13.sp)
            IconButton(onClick={anchor=next;selected=DailyTools.periodDays(anchor,monthly).first()},enabled=next<=today,modifier=Modifier.semantics{contentDescription=if(monthly)tr(R.string.ui_4d5a52448a39, "下月") else tr(R.string.ui_7f5fab4d966e, "下周")}){LineIcon("next",if(next<=today)c.secondary else c.secondary.copy(alpha=.3f))}
        }
    }){
        Column(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(if(monthly)tr(R.string.ui_df7fe8cc8fa7, "该月") else tr(R.string.ui_392eb9dbee9d, "该周"),fontSize=12.sp,color=c.secondary)
            Text(buildAnnotatedString{
                Regex("h|min|[0-9<]+").findAll(DailyTools.compact(total)).forEach{part->
                    val unit=part.value=="h"||part.value=="min"
                    withStyle(SpanStyle(fontSize=if(unit)17.sp else 32.sp,color=if(unit)c.secondary else c.onSurface)){append(part.value)}
                }
            },fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.Light,modifier=Modifier.testTag("period-total"))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
            TextButton(onClick={calendar=false},modifier=Modifier.testTag("stats-bars")){Text(tr(R.string.ui_fe876b69882f, "柱图"),color=if(!calendar)c.primary else c.secondary)}
            TextButton(onClick={selected=statisticsSelectionForPeriod(anchor,selected,today,monthly=true);calendar=true;monthly=true},modifier=Modifier.testTag("stats-calendar")){Text(tr(R.string.ui_a00f295cfbb0, "日历"),color=if(calendar)c.primary else c.secondary)}
        }
        if(calendar)FocusMonth(s,anchor,today,selected){selected=it}
        else FocusBars(s,days,today,selected,monthly){selected=it}
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Text("${selected.monthValue}.${selected.dayOfMonth}",fontSize=13.sp)
            Text(if(selected>today)tr(R.string.ui_16cbfc8b379d, "未到") else DailyTools.compact(s.daily[selected.toString()]?:0L),fontSize=14.sp,modifier=Modifier.testTag("selected-day-total"))
        }
        TextButton(onClick={explanation=!explanation}){Text(tr(R.string.ui_2d51579e3dfb, "统计说明 %1\$s", if(explanation)"−" else "＋"),fontSize=12.sp)}
        if(explanation)Text(tr(R.string.ui_69d0df4976e3, "仅计专注计时实际专注。暂停、休息与应用目标用时不计；提前结束也保留已专注部分。"),fontSize=12.sp,color=c.secondary)
        if(s.recoveryNote.isNotEmpty())Text(s.recoveryNote,fontSize=12.sp,color=c.secondary)
    }
}

@Composable private fun FocusBars(s:AppState,days:List<LocalDate>,today:LocalDate,selected:LocalDate,monthly:Boolean,onSelect:(LocalDate)->Unit){
    val c=MaterialTheme.colorScheme
    val maxMinutes=maxOf(180.0,(days.maxOfOrNull{s.daily[it.toString()]?:0L}?:0L)/60000.0)
    val ceiling=ceil(maxMinutes/180)*180
    val radius=when(ThemeCards.base(s.settings.theme)){"paper"->2.dp;"forest"->5.dp;"dusk"->6.dp;else->10.dp}
    Row(Modifier.fillMaxWidth().padding(top=10.dp).testTag("focus-chart")){
        Box(Modifier.width(30.dp).height(164.dp)){
            repeat(4){i->Box(Modifier.offset(y=(140f*i/3-12).dp).height(24.dp),contentAlignment=Alignment.CenterStart){
                Text((ceiling*(3-i)/3).toInt().toString(),fontSize=10.sp,color=c.secondary)
            }}
        }
        Box(Modifier.weight(1f)){
            Canvas(Modifier.fillMaxWidth().height(140.dp)){
                repeat(4){i->val y=size.height*i/3;drawLine(c.outlineVariant.copy(alpha=.55f),Offset(0f,y),Offset(size.width,y),1.dp.toPx(),pathEffect=if(i==3)null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),5.dp.toPx())))}
            }
            val scrolling=rememberScrollState()
            Row(if(monthly)Modifier.horizontalScroll(scrolling)else Modifier.fillMaxWidth()){
                days.forEach{day->
                    val ms=s.daily[day.toString()]?:0L;val ratio=(ms/60000.0/ceiling).coerceIn(0.0,1.0)
                    val modifier=if(monthly)Modifier.width(44.dp)else Modifier.weight(1f)
                    Column(modifier.height(164.dp).clickable{onSelect(day)}.semantics{contentDescription="$day，${if(day>today)tr(R.string.ui_16cbfc8b379d, "未到") else durationText(ms)}";this.selected=day==selected},horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.fillMaxWidth().height(140.dp),contentAlignment=Alignment.BottomCenter){
                            if(ms>0)Box(Modifier.width(14.dp).height((140*ratio).toFloat().dp).clip(RoundedCornerShape(topStart=radius,topEnd=radius))
                                .background(Brush.verticalGradient(listOf(lerp(c.surface,c.primary,if(day==selected).95f else .58f),lerp(c.surface,c.primary,if(day==selected).55f else .30f)))))
                            else Box(Modifier.size(4.dp).border(1.dp,c.secondary.copy(alpha=if(day>today).22f else .5f),RoundedCornerShape(50)))
                        }
                        Text(if(monthly)day.dayOfMonth.toString() else listOf(tr(R.string.ui_51a75f4634df, "一"),tr(R.string.ui_084b42f6e95e, "二"),tr(R.string.ui_a4c3313deb18, "三"),tr(R.string.ui_754a9d5828d5, "四"),tr(R.string.ui_c9b87f516a38, "五"),tr(R.string.ui_de07b5383874, "六"),tr(R.string.ui_85217f7aff77, "日"))[day.dayOfWeek.value-1],fontSize=11.sp,color=if(day==selected)c.primary else c.secondary,modifier=Modifier.padding(top=6.dp))
                    }
                }
            }
        }
    }
    Text(if(monthly)tr(R.string.ui_c213b49a408e, "min · 横滑查看") else "min",Modifier.fillMaxWidth(),textAlign=TextAlign.End,fontSize=11.sp,color=c.secondary)
}

@Composable private fun FocusMonth(s:AppState,anchor:LocalDate,today:LocalDate,selected:LocalDate,onSelect:(LocalDate)->Unit){
    val days=DailyTools.periodDays(anchor,true);val c=MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val list=maxWidth<280.dp||LocalDensity.current.fontScale>1.2f
        @Composable fun Day(day:LocalDate,modifier:Modifier){
            val ms=s.daily[day.toString()]?:0L;val shape=RoundedCornerShape(if(ThemeCards.base(s.settings.theme)=="paper")4.dp else 16.dp)
            Box(modifier.heightIn(min=42.dp).padding(2.dp).clip(shape).background(c.primary.copy(alpha=(ms/7_200_000f).coerceIn(0f,.22f)))
                .border(1.dp,if(day==selected)c.primary.copy(alpha=.6f)else Color.Transparent,shape).clickable{onSelect(day)}.testTag("day-$day")
                .semantics{contentDescription="$day，${if(day>today)tr(R.string.ui_16cbfc8b379d, "未到") else durationText(ms)}"}.padding(5.dp),contentAlignment=Alignment.Center){
                Text(if(list)"${day.monthValue}.${day.dayOfMonth} · ${if(day>today)tr(R.string.ui_16cbfc8b379d, "未到") else DailyTools.compact(ms)}"else day.dayOfMonth.toString(),fontSize=12.sp,color=if(day>today)c.secondary.copy(alpha=.5f)else c.onSurface)
            }
        }
        Column{
            if(list)days.forEach{Day(it,Modifier.fillMaxWidth())}
            else{
                Row{listOf(tr(R.string.ui_51a75f4634df, "一"),tr(R.string.ui_084b42f6e95e, "二"),tr(R.string.ui_a4c3313deb18, "三"),tr(R.string.ui_754a9d5828d5, "四"),tr(R.string.ui_c9b87f516a38, "五"),tr(R.string.ui_de07b5383874, "六"),tr(R.string.ui_85217f7aff77, "日")).forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=11.sp,color=c.secondary)}}
                val offset=days.first().dayOfWeek.value-1
                repeat((offset+days.size+6)/7){r->Row{repeat(7){col->val index=r*7+col-offset;if(index in days.indices)Day(days[index],Modifier.weight(1f))else Spacer(Modifier.weight(1f).height(42.dp))}}}
            }
        }
    }
}

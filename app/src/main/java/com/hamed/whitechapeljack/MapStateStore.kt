package com.hamed.whitechapeljack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PublicPolice(val point: Int, val color: String)
data class PublicEvent(val house: Int, val type: String)
data class HellToken(val point: Int, val color: String, val real: Boolean)

object MapStateStore {
    private const val PREF = "whitechapel_map_state"
    private const val KEY = "state"

    private fun defaults() = JSONObject()
        .put("police", JSONArray())
        .put("events", JSONArray())
        .put("hellPolice", JSONArray())
        .put("hellVictims", JSONArray())
        .put("hellReveal", false)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        publish(ctx)
    }

    private fun get(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        return raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: defaults()
    }

    private fun put(ctx: Context, o: JSONObject) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        publish(ctx, o)
    }

    fun testPolice(ctx: Context): List<PublicPolice> {
        val a = get(ctx).optJSONArray("police") ?: JSONArray()
        return List(a.length()) { i -> val o=a.getJSONObject(i); PublicPolice(o.getInt("point"), o.optString("color", "#2368b2")) }
    }

    fun upsertTestPolice(ctx: Context, point: Int, color: String) {
        val o = get(ctx); val a=o.optJSONArray("police") ?: JSONArray(); val out=JSONArray(); var replaced=false
        for(i in 0 until a.length()) { val x=a.getJSONObject(i); if(x.optString("color")==color){out.put(JSONObject().put("point",point).put("color",color));replaced=true}else out.put(x) }
        if(!replaced) out.put(JSONObject().put("point",point).put("color",color)); o.put("police",out); put(ctx,o)
    }

    fun removeTestPolice(ctx: Context, color: String) {
        val o=get(ctx); val a=o.optJSONArray("police")?:JSONArray(); val out=JSONArray()
        for(i in 0 until a.length()){ val x=a.getJSONObject(i); if(x.optString("color")!=color) out.put(x) }
        o.put("police",out); put(ctx,o)
    }

    fun events(ctx: Context): List<PublicEvent> {
        val a=get(ctx).optJSONArray("events")?:JSONArray()
        return List(a.length()){i->val x=a.getJSONObject(i);PublicEvent(x.getInt("house"),x.getString("type"))}
    }

    fun addEvent(ctx: Context, house: Int, type: String) {
        val o=get(ctx); val a=o.optJSONArray("events")?:JSONArray(); a.put(JSONObject().put("house",house).put("type",type)); o.put("events",a); put(ctx,o)
    }

    fun clearEvents(ctx: Context) { val o=get(ctx);o.put("events",JSONArray());put(ctx,o) }

    fun hellPolice(ctx: Context): List<HellToken> = hellList(get(ctx),"hellPolice")
    fun hellVictims(ctx: Context): List<HellToken> = hellList(get(ctx),"hellVictims")
    fun hellReveal(ctx: Context): Boolean = get(ctx).optBoolean("hellReveal",false)

    private fun hellList(o: JSONObject,key:String):List<HellToken>{
        val a=o.optJSONArray(key)?:JSONArray()
        return List(a.length()){i->val x=a.getJSONObject(i);HellToken(x.getInt("point"),x.optString("color","#777777"),x.optBoolean("real",true))}
    }

    fun setHellPolice(ctx: Context, list: List<HellToken>) { val o=get(ctx);o.put("hellPolice",hellArray(list));put(ctx,o) }
    fun setHellVictims(ctx: Context, list: List<HellToken>) { val o=get(ctx);o.put("hellVictims",hellArray(list));put(ctx,o) }
    fun setHellReveal(ctx: Context, value:Boolean){val o=get(ctx);o.put("hellReveal",value);put(ctx,o)}

    private fun hellArray(list:List<HellToken>):JSONArray{val a=JSONArray();list.forEach{a.put(JSONObject().put("point",it.point).put("color",it.color).put("real",it.real))};return a}

    fun publish(ctx: Context) = publish(ctx, get(ctx))

    private fun publish(ctx: Context, o: JSONObject) {
        val houses = loadPoints(ctx,"houses.json").associateBy{it.number}
        val policePoints = loadPoints(ctx,"polises.json").associateBy{it.number}
        val pub=JSONObject(); val pa=JSONArray(); val ea=JSONArray(); val hp=JSONArray(); val hv=JSONArray()
        val p=o.optJSONArray("police")?:JSONArray()
        for(i in 0 until p.length()){val x=p.getJSONObject(i);policePoints[x.getInt("point")]?.let{pt->pa.put(JSONObject().put("x",pt.normX).put("y",pt.normY).put("color",x.optString("color","#2368b2")))}}
        val e=o.optJSONArray("events")?:JSONArray()
        for(i in 0 until e.length()){val x=e.getJSONObject(i);houses[x.getInt("house")]?.let{pt->ea.put(JSONObject().put("x",pt.normX).put("y",pt.normY).put("type",x.getString("type")))}}
        val reveal=o.optBoolean("hellReveal",false)
        val hpa=o.optJSONArray("hellPolice")?:JSONArray()
        for(i in 0 until hpa.length()){
            val x=hpa.getJSONObject(i)
            policePoints[x.getInt("point")]?.let{pt->
                val item=JSONObject().put("x",pt.normX).put("y",pt.normY)
                if(reveal) item.put("color",x.optString("color","#2368b2")).put("real",x.optBoolean("real",true))
                hp.put(item)
            }
        }
        val hva=o.optJSONArray("hellVictims")?:JSONArray()
        for(i in 0 until hva.length()){
            val x=hva.getJSONObject(i)
            houses[x.getInt("point")]?.let{pt->
                val item=JSONObject().put("x",pt.normX).put("y",pt.normY)
                if(reveal) item.put("color",x.optString("color","#9a286e")).put("real",x.optBoolean("real",true))
                hv.put(item)
            }
        }
        pub.put("police",pa).put("events",ea).put("hellPolice",hp).put("hellVictims",hv).put("hellReveal",reveal)
        TvMapHub.setState(pub)
    }

    private fun loadPoints(ctx: Context,file:String):List<BoardPoint>{
        val arr=JSONArray(ctx.assets.open(file).bufferedReader().use{it.readText()}); val out=mutableListOf<BoardPoint>()
        for(i in 0 until arr.length()){val x=arr.getJSONObject(i);out+=BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}
        return out
    }
}

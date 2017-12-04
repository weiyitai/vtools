package com.omarea.vboot

import android.content.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.omarea.shared.*
import com.omarea.shell.units.BatteryUnit

import kotlinx.android.synthetic.main.layout_battery.*

import java.util.Timer
import java.util.TimerTask


class fragment_battery : Fragment() {

    lateinit internal var view: View

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        view = inflater!!.inflate(R.layout.layout_battery, container, false)
        return view
    }

    internal var myHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
        }
    }

    internal var timer: Timer? = null
    lateinit internal var batteryMAH: String

    internal var temp = 0.0
    internal var level = 0
    internal var powerChonnected = false
    internal var voltage: Double = 0.toDouble()
    private var batteryUnits = BatteryUnit()
    private lateinit var spf:SharedPreferences

    override fun onResume() {
        if (spf!=null){
            spf.registerOnSharedPreferenceChangeListener{ sharedPreferences, key ->

            }
        }

        settings_qc.isChecked = spf.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false)
        settings_bp.isChecked = spf.getBoolean(SpfConfig.CHARGE_SPF_BP, false)
        settings_bp_level.setText(spf.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85).toString())
        settings_qc_limit.setText(spf.getInt(SpfConfig.CHARGE_SPF_QC_LIMIT,4000).toString())

        if (broadcast == null) {
            broadcast = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    try {
                        temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).toDouble()
                        temp = temp / 10.0
                        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toDouble()
                        if (voltage > 1000)
                            voltage = voltage / 1000.0
                        if (voltage > 100)
                            voltage = voltage / 100.0
                        else if (voltage > 10)
                            voltage = voltage / 10.0
                        powerChonnected = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING) == BatteryManager.BATTERY_STATUS_CHARGING
                        //intent.getDoubleExtra(BatteryManager.EXTRA_HEALTH);
                    } catch (ex: Exception) {
                        print(ex.message)
                    }

                }
            }
            val ACTION_BATTERY_CHANGED = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(broadcast, ACTION_BATTERY_CHANGED)
        }

        val battrystatus = view.findViewById(R.id.battrystatus) as TextView
        val powerstatus = view.findViewById(R.id.powerstatus) as TextView
        batteryMAH = batteryUnits.batteryMAH + "   "
        val context = context.applicationContext
        serviceRunning = BatteryService.serviceIsRunning(context)

        timer = Timer()

        timer!!.schedule(object : TimerTask() {
            override fun run() {
                myHandler.post {
                    battrystatus.text = "电池信息：" +
                            batteryMAH +
                            temp + "°C   " +
                            level + "%    " +
                            voltage + "v"

                    var changeMAH = batteryUnits.changeMAH
                    powerstatus.text = "充电速度：" +
                            (if (powerChonnected) "+" else "-") + changeMAH + "      " +
                            if (spf.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false) && serviceRunning) "充电已加速" else "未加速"
                }
            }
        }, 0, 3000)

        super.onResume()
    }

    internal var serviceRunning = false

    override fun onPause() {
        super.onPause()
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }

        try {
            if (broadcast != null)
                context.unregisterReceiver(broadcast)
        } catch (ex: Exception) {

        }

    }

    override fun onDestroy() {
        try {
            if (broadcast != null)
                context.unregisterReceiver(broadcast)
        } catch (ex: Exception) {

        }

        super.onDestroy()
    }

    internal var broadcast: BroadcastReceiver? = null
    lateinit internal var cmdshellTools: cmd_shellTools

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        spf = context.getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)

        settings_qc.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, settings_qc.isChecked).commit()
            if(!settings_qc.isChecked){
                Snackbar.make(this.view,"充电加速服务已禁用，可能需要重启手机才能恢复默认设置！",Snackbar.LENGTH_SHORT).show()
            } else{
                //启用电池服务
                startBatteryService()
                Snackbar.make(this.view,"OK！如果你要手机重启后自动开启本功能，请允许微工具箱开机自启！",Snackbar.LENGTH_SHORT).show()
            }
        }
        settings_bp.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_BP, settings_bp.isChecked).commit()
            //禁用电池保护：恢复充电功能
            if(!settings_bp.isChecked){
                cmdshellTools.DoCmdSync(Consts.ResumeChanger)
            }
            else {
                //启用电池服务
                startBatteryService()
                Snackbar.make(this.view,"OK！如果你要手机重启后自动开启本功能，请允许微工具箱开机自启！",Snackbar.LENGTH_SHORT).show()
            }
        }
        settings_bp_level.setOnEditorActionListener { v, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT){
                val level:Int
                try{
                    level = settings_bp_level.text.toString().toInt()
                    spf.edit().putInt(SpfConfig.CHARGE_SPF_BP_LEVEL, level).commit()
                    Snackbar.make(this.view, "设置已保存，稍后生效。当前限制等级："+ settings_bp_level.text, Snackbar.LENGTH_SHORT ).show()
                    startBatteryService();
                }
                catch(e:Exception){
                }
            }
            false
        }
        settings_qc_limit.setOnEditorActionListener { v, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT){
                val level:Int
                try{
                    level = settings_qc_limit.text.toString().toInt()
                    spf.edit().putInt(SpfConfig.CHARGE_SPF_QC_LIMIT, level).commit()
                    Snackbar.make(this.view, "设置已保存。当前限制速度："+ settings_qc_limit.text + "mA", Snackbar.LENGTH_SHORT ).show()
                    startBatteryService();
                }
                catch(e:Exception){
                }
            }
            false
        }
    }

    //启动电池服务
    private fun startBatteryService() {
        try{
            val intent = Intent( context, BatteryService::class.java)
            context.startService(intent)
        } catch (ex:Exception){
        }
    }

    companion object {
        fun Create(thisView: main, cmdshellTools: cmd_shellTools): Fragment {
            val fragment = fragment_battery()
            fragment.cmdshellTools = cmdshellTools
            return fragment
        }
    }
}
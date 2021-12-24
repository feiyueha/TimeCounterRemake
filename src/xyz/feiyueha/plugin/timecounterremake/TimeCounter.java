package xyz.feiyueha.plugin.timecounterremake;

import com.google.common.util.concurrent.Service;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class TimeCounter extends JavaPlugin implements Listener {
    public long autoSaveTime = 120;//自动保存时长
    public RecordSaveTask task;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        loadConfig(getDataFolder() + File.separator + "playerData.yml");
        autoSaveTime = getConfig().getLong("Setting.autoSaveTime");
        boolean DatabaseStat = getConfig().getBoolean("Database.status");
        String host = getConfig().getString("Database.host");
        String user = getConfig().getString("Database.user");
        String password = getConfig().getString("Database.password");
        String charset = getConfig().getString("Database.charset");
        int port = getConfig().getInt("Database.port");
        String database = getConfig().getString("Database.database");//读取相关的配置文件
        StringBuffer sb = new StringBuffer();
        sb.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
        sb.append("?useUnicode=true&characterEncoding=").append(charset).append("&useSSL=false");
        if(DatabaseStat){
            try{//尝试数据库是否能够连接
                Connection con = DriverManager.getConnection(sb.toString(),user,password);
                getLogger().info("尝试连接数据库成功！");
                checkTable(con);//此处开始判断数据库中的TimeCounter表是否被创建
                con.close();
            }catch(SQLException e){
                getLogger().warning("连接数据库失败！");
                DatabaseStat = false;
                e.printStackTrace();e.printStackTrace();
            }
        }else{
            getLogger().warning("数据库未开启！");
        }
        task = new RecordSaveTask(DatabaseStat,sb.toString(),user,password,this);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        task.runTaskTimer(this,autoSaveTime*60*1000,autoSaveTime*60*1000);
    }

    @Override
    public void onDisable() {
        Collection<? extends Player> playerList = getServer().getOnlinePlayers();
        task.run();
        getLogger().info("已自动保存玩家在线时长："+playerList.size());
        task.cancel();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {//玩家加入事件
        Player p = event.getPlayer();
        FileConfiguration fc = loadConfig(getDataFolder() + File.separator + "playerData.yml");
        fc.set("LatestTime." + p.getName(), System.currentTimeMillis());
        try {
            fc.save(new File(getDataFolder() + File.separator + "playerData.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Thread th = new Thread(task);//利用线程来保存玩家的数据，防止服务器因连接数据库缓慢而导致的卡顿
        th.start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("tc")) {
            if (!(sender instanceof Player)) {//控制台显示
                getLogger().info("控制台暂不支持查看玩家的在线统计，请在数据库以及游戏内查看！");
                return true;
            } else if (args.length == 1) {
                switch (args[0]) {
                    case "list"://查看时长排行，输出给该用户
                        list((Player) sender);
                        break;
                }
                return true;
            }
        }
        return false;
    }

    private void list(Player player) {
        Thread th = new Thread(task);//利用线程来保存玩家的数据，防止服务器因连接数据库缓慢而导致的卡顿
        th.start();
        FileConfiguration fc = loadConfig(getDataFolder() + File.separator + "playerData.yml");
        long nowTime = System.currentTimeMillis();
        ConfigurationSection cs = fc.getConfigurationSection("TotalTime");
        Set<String> keys = cs.getKeys(false);
        Map<String, Long> map = new HashMap<>();
        for(String key :keys) {
            map.put(key, cs.getLong(key));
        }
        List<Map.Entry<String, Long>> list = new ArrayList<>(map.entrySet());
        list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        player.sendMessage(ChatColor.GOLD+"==========在线时长排行==========");
        int num = 0;
        for (Map.Entry<String, Long> mapping : list) {
            num++;
            StringBuffer msg = new StringBuffer();
            msg.append("=");
            msg.append(num).append(".");
            msg.append(mapping.getKey());
            msg.append(" - ");
            long SSS = mapping.getValue()%1000;
            long ss = (mapping.getValue()/1000)%60;
            long mm = (mapping.getValue()/60000)%60;
            long hh = (mapping.getValue()/3600000)%24;
            long dd = mapping.getValue()/86400000;
            msg.append(dd).append("d").append(hh).append("h").append(mm).append("min").append(ss).append("s").append(SSS).append("ms");
            player.sendMessage(ChatColor.GOLD+msg.toString());
            if(num >= 5) {//最多统计五个人
                break;
            }
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(nowTime);
        player.sendMessage(ChatColor.GOLD+"==================================");
        player.sendMessage(ChatColor.GOLD+"=该统计截止至 "+simpleDateFormat.format(date)+"=");
        player.sendMessage(ChatColor.GOLD+"==================================");
    }

    private FileConfiguration loadConfig(String path) {//读取插件的配置文件
        File file = new File(path);
        if (!(file.exists())) {
            try {
                if(file.createNewFile()){
                    getLogger().info("已创建新的配置文件");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void checkTable(Connection con){
        String sql = "SELECT * FROM timecount";
        try {
            ResultSet rs = con.createStatement().executeQuery(sql);
        } catch (SQLException e) {
            //该表不存在，跳转到catch语句中开始创建数据表
            String createTableSQL = "CREATE TABLE timecount("
                    + "Name VARCHAR(100) NOT NULL, "
                    + "Time BIGINT(20) NOT NULL"
                    + ")";
            try {
                PreparedStatement ps = con.prepareStatement(createTableSQL);
                ps.executeUpdate();
                ps.close();
                getLogger().info("创建数据库表成功");
            } catch (SQLException ex) {//数据表创建失败，打印错误信息
                getLogger().warning("创建储存的数据表失败，详情异常请查看日志");
                ex.printStackTrace();
            }
        }
    }
}
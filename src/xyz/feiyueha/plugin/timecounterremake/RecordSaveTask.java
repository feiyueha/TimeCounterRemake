package xyz.feiyueha.plugin.timecounterremake;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Collection;

public class RecordSaveTask extends BukkitRunnable {
    public boolean DatabaseStat;//数据库选项是否被开启
    public String DatabaseUrl;//数据库地址
    public String user;//数据库用户名
    public String password;//数据库密码
    public JavaPlugin jp;//JavaPlugin对象

    public RecordSaveTask(boolean databaseStat, String databaseUrl, String user, String password, JavaPlugin jp) {
        DatabaseStat = databaseStat;
        DatabaseUrl = databaseUrl;
        this.user = user;
        this.password = password;
        this.jp = jp;
    }

    //这里写具体的保存时间的算法
    @Override
    public void run(){
        FileConfiguration fc = loadConfig(jp.getDataFolder() + File.separator + "playerData.yml");//读取配置文件
        Collection<? extends Player> playerList = jp.getServer().getOnlinePlayers();//获取所有玩家的列表
        Connection con = null;
        if(DatabaseStat) {//连接数据库
            try {
                Class.forName("com.mysql.jdbc.Driver");
                con = DriverManager.getConnection(DatabaseUrl, user, password);
            } catch (Exception e) {
                jp.getLogger().warning("连接数据库失败！");
                DatabaseStat = false;
                e.printStackTrace();
            }
        }
        for(Player p :playerList) {
            if(fc.contains("LatestTime." + p.getName())) {
                long latestTime = fc.getLong("LatestTime."+p.getName());
                long nowTime = System.currentTimeMillis();
                long lastTime = nowTime - latestTime;
                long newTotal;
                if(fc.contains("TotalTime."+p.getName())) {
                    newTotal =  fc.getLong("TotalTime."+p.getName())+lastTime;
                }else {
                    newTotal = lastTime;
                }
                fc.set("TotalTime."+p.getName(), newTotal);
                fc.set("LatestTime." + p.getName(), nowTime);
                try {
                    fc.save(new File(jp.getDataFolder() + File.separator + "playerData.yml"));
                } catch (IOException e) {
                    jp.getLogger().warning("玩家数据保存失败！");
                    e.printStackTrace();
                }
                if(DatabaseStat) {//向数据库传输消息
                    try {
                        String sql = "SELECT * FROM timecount where Name=?";
                        PreparedStatement ps = con.prepareStatement(sql);
                        ps.setString(1, p.getName());
                        ResultSet rs = ps.executeQuery();
                        PreparedStatement ps2;
                        if (rs.next()) {//有数据，则更新
                            String sql2 = "update timecount set Time= ? where Name= ? ";
                            ps2 = con.prepareStatement(sql2);
                            ps2.setLong(1, newTotal);
                            ps2.setString(2, p.getName());
                            ps2.execute();
                        } else {//没有数据，则创建
                            String sql2 = "insert into timecount values (?,?)";
                            ps2 = con.prepareStatement(sql2);
                            ps2.setString(1, p.getName());
                            ps2.setLong(2, newTotal);
                            ps2.execute();
                        }
                        ps2.close();
                        ps.close();
                        rs.close();
                    } catch (Exception e) {
                        jp.getLogger().warning("写入数据库失败！");
                        DatabaseStat = false;
                        e.printStackTrace();
                    }
                }
            }else {
                jp.getLogger().warning(p.getName()+":玩家数据异常");
            }
        }
        if(DatabaseStat) {//关闭数据库连接
            try {
                con.close();
            } catch (SQLException e) {
                jp.getLogger().warning("连接数据库失败！");
                DatabaseStat = false;
                e.printStackTrace();
            }
        }
        jp.getLogger().info("已自动保存并更新玩家在线时长："+playerList.size());
    }

    private FileConfiguration loadConfig(String path) {//读取配置文件
        File file = new File(path);
        if (!(file.exists())) {
            try {
                file.createNewFile();
                jp.getLogger().info("已创建新的配置文件");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
package oni.discord.troll;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author: hpr
 * @date: 31/12/2018
 */
public class Main {

    private static Logger logger = LoggerFactory.getLogger(Main.class);
    private static TrollBot trollBot;

    public static void main(String[] args) {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(new File("trollbot.properties")));
            JDA jda = new JDABuilder(properties.getProperty("bot.secret")).build();
            jda.awaitReady();
            trollBot = new TrollBot(jda, properties);
        } catch (LoginException e) {
            logger.error("Error logging into discord", e);
            System.exit(-1);
        } catch (InterruptedException e) {
            logger.error("Error while waiting for bot to get ready", e);
            System.exit(-1);
        } catch (IOException e) {
            logger.error("Error while reading properties", e);
            System.out.println("Make sure you have troll.properties in classpath");
            System.exit(-1);
        } catch (NullPointerException e) {
            logger.error("Error while reading properties", e);
            System.out.println("Make sure you have troll.properties in classpath");
            System.exit(-1);
        }
    }
}

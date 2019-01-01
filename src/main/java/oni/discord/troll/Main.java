/*
 * This file is part of Discord-Troll application.
 *
 * Discord-Troll application is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord-Troll application is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Discord-Troll application.  If not, see <http://www.gnu.org/licenses/>.
 */

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

/*
Copyright (C) 2022 Nep Nep
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with Minecraft
(or a modified version of that library), containing parts covered by the terms of the Minecraft End User License Agreement,
the licensors of this Program grant you additional permission to convey the resulting work.
*/

package club.bottomservices.discordrpc.fabricmod;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

import java.util.List;

@Config(name = "yarpc")
public class YARPCConfig implements ConfigData {
    // Must be static to prevent from being detected
    private static final List<String> VALID_OPTIONS = List.of("DIMENSION", "USERNAME", "HEALTH", "HUNGER", "SERVER", "HELD_ITEM");
    private static final String[] DEFAULT = new String[]{"USERNAME", "HEALTH", "HUNGER", "DIMENSION"};

    @Comment("Whether this mod is enabled")
    boolean isEnabled = true;
    @Comment("Format for the state line, use %s as a placeholder, up to 2 placeholders are allowed")
    String stateFormat = "%s | %s";
    @Comment("Format for the details line, use %s as a placeholder, up to 2 placeholders are allowed")
    String detailsFormat = "%s | %s";
    @Comment("Application id from discord for using custom assets, see https://discord.com/developers/applications/")
    String appId = "928401525842259979";
    @Comment("List of format arguments (DIMENSION, USERNAME, HEALTH, HUNGER, SERVER, HELD_ITEM)")
    String[] formatArgs = DEFAULT;
    @Comment("Text for the large image")
    String largeText = "Playing minecraft";
    @Comment("Text for the small image")
    String smallText = "With YARPC";
    @Comment("Key for the large image (Only change this if using a custom application!)")
    String largeImage = "";
    @Comment("Key for the small image (Only change this if using a custom application!)")
    String smallImage = "";

    @Override
    public void validatePostLoad() {
        for (var arg : formatArgs) {
            if (!VALID_OPTIONS.contains(arg)) {
                formatArgs = DEFAULT;
                break;
            }
        }
    }
}

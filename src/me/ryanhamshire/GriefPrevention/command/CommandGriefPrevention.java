/*
 * This file is part of GriefPrevention, licensed under the MIT License (MIT).
 *
 * Copyright (c) Ryan Hamshire
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.ryanhamshire.GriefPrevention.command;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.annotation.NonnullByDefault;

@NonnullByDefault
public class CommandGriefPrevention {

    private static final String INDENT = "    ";
    private static final String LONG_INDENT = INDENT + INDENT;

    /**
     * Create a new instance of the GriefPrevention command structure.
     *
     * @return The newly created command
     */
    public static CommandSpec getCommand() {
        return CommandSpec.builder()
                .description(Texts.of("Text description"))
                .extendedDescription(Texts.of("commands:\n",
                        INDENT, title("abandonallclaims"), LONG_INDENT, "Deletes ALL your claims\n",
                        INDENT, title("abandonclaim"), LONG_INDENT, "Deletes a claim\n",
                        INDENT, title("abandontoplevelclaim"), LONG_INDENT, "Deletes a claim and all its subdivisions\n",
                        INDENT, title("flag"), LONG_INDENT, "Toggles various flags in claims\n",
                        INDENT, title("ignoreclaims"), LONG_INDENT, "Toggles ignore claims mode\n"))
                .children(GriefPrevention.instance.registerSubCommands())
                .build();
    }

    private static Text title(String title) {
        return Texts.of(TextColors.GREEN, title);
    }
}

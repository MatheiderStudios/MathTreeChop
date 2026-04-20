package com.matheider.MathTreeChop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HEX {

    private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_AMPERSAND_DOUBLE_HASH = Pattern.compile("&##([A-Fa-f0-9]{6})");
    private static final Pattern HEX_ANGLE_BRACKET = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
        "<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)(?:</gradient>|$)",
        Pattern.DOTALL
    );
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    public String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        message = applyGradients(message);
        message = applyHexColors(message);
        message = applyLegacyColors(message);

        return message;
    }

    public Component colorizeToComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }

        String colorized = colorize(message);
        return parseToComponent(colorized);
    }

    private Component parseToComponent(String message) {
        List<Component> components = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        TextColor currentColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int i = 0;
        while (i < message.length()) {
            if (message.charAt(i) == '§' && i + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(i + 1));

                if (currentText.length() > 0) {
                    Component comp = buildComponent(currentText.toString(), currentColor, bold, italic, underlined, strikethrough, obfuscated);
                    components.add(comp);
                    currentText = new StringBuilder();
                }

                if (code == 'x' && i + 13 < message.length()) {
                    StringBuilder hexBuilder = new StringBuilder();
                    boolean valid = true;
                    for (int j = 0; j < 6; j++) {
                        int pos = i + 2 + (j * 2);
                        if (pos + 1 < message.length() && message.charAt(pos) == '§') {
                            hexBuilder.append(message.charAt(pos + 1));
                        } else {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        try {
                            currentColor = TextColor.fromHexString("#" + hexBuilder);
                            i += 14;
                            continue;
                        } catch (Exception ignored) {
                        }
                    }
                }

                switch (code) {
                    case '0' -> currentColor = TextColor.fromHexString("#000000");
                    case '1' -> currentColor = TextColor.fromHexString("#0000AA");
                    case '2' -> currentColor = TextColor.fromHexString("#00AA00");
                    case '3' -> currentColor = TextColor.fromHexString("#00AAAA");
                    case '4' -> currentColor = TextColor.fromHexString("#AA0000");
                    case '5' -> currentColor = TextColor.fromHexString("#AA00AA");
                    case '6' -> currentColor = TextColor.fromHexString("#FFAA00");
                    case '7' -> currentColor = TextColor.fromHexString("#AAAAAA");
                    case '8' -> currentColor = TextColor.fromHexString("#555555");
                    case '9' -> currentColor = TextColor.fromHexString("#5555FF");
                    case 'a' -> currentColor = TextColor.fromHexString("#55FF55");
                    case 'b' -> currentColor = TextColor.fromHexString("#55FFFF");
                    case 'c' -> currentColor = TextColor.fromHexString("#FF5555");
                    case 'd' -> currentColor = TextColor.fromHexString("#FF55FF");
                    case 'e' -> currentColor = TextColor.fromHexString("#FFFF55");
                    case 'f' -> currentColor = TextColor.fromHexString("#FFFFFF");
                    case 'k' -> obfuscated = true;
                    case 'l' -> bold = true;
                    case 'm' -> strikethrough = true;
                    case 'n' -> underlined = true;
                    case 'o' -> italic = true;
                    case 'r' -> {
                        currentColor = null;
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                        obfuscated = false;
                    }
                    default -> {
                    }
                }
                i += 2;
            } else {
                currentText.append(message.charAt(i));
                i++;
            }
        }

        if (currentText.length() > 0) {
            Component comp = buildComponent(currentText.toString(), currentColor, bold, italic, underlined, strikethrough, obfuscated);
            components.add(comp);
        }

        Component result = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (Component comp : components) {
            result = result.append(comp);
        }

        return result;
    }

    private Component buildComponent(String text, TextColor color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
        Component component = Component.text(text)
            .decoration(TextDecoration.ITALIC, italic ? TextDecoration.State.TRUE : TextDecoration.State.FALSE)
            .decoration(TextDecoration.BOLD, bold ? TextDecoration.State.TRUE : TextDecoration.State.FALSE)
            .decoration(TextDecoration.UNDERLINED, underlined ? TextDecoration.State.TRUE : TextDecoration.State.FALSE)
            .decoration(TextDecoration.STRIKETHROUGH, strikethrough ? TextDecoration.State.TRUE : TextDecoration.State.FALSE)
            .decoration(TextDecoration.OBFUSCATED, obfuscated ? TextDecoration.State.TRUE : TextDecoration.State.FALSE);

        if (color != null) {
            component = component.color(color);
        }

        return component;
    }

    private String applyGradients(String message) {
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);

            String gradientText = createGradient(text, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String createGradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Color startColor = hexToColor(startHex);
        Color endColor = hexToColor(endHex);

        StringBuilder result = new StringBuilder();

        String strippedText = text
            .replaceAll("&[0-9a-fA-Fk-oK-OrR]", "")
            .replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        int visibleLength = strippedText.replaceAll("\\s", "").length();

        if (visibleLength == 0) {
            return text;
        }

        int colorIndex = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') ||
                    (next >= 'A' && next <= 'F') || (next >= 'k' && next <= 'o') ||
                    (next >= 'K' && next <= 'O') || next == 'r' || next == 'R') {
                    result.append("&").append(next);
                    i += 2;
                    continue;
                }
            }

            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                result.append('§').append(text.charAt(i + 1));
                i += 2;
                continue;
            }

            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                result.append(c);
                i++;
                continue;
            }

            double ratio = visibleLength == 1 ? 0 : (double) colorIndex / (visibleLength - 1);
            Color interpolated = interpolateColor(startColor, endColor, ratio);
            String hexColor = colorToHex(interpolated);

            result.append(translateHexToMinecraft(hexColor));
            result.append(c);
            colorIndex++;
            i++;
        }

        return result.toString();
    }

    private Color hexToColor(String hex) {
        return new Color(
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        );
    }

    private String colorToHex(Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private Color interpolateColor(Color start, Color end, double ratio) {
        int red = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
        int green = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int blue = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));

        red = Math.clamp(red, 0, 255);
        green = Math.clamp(green, 0, 255);
        blue = Math.clamp(blue, 0, 255);

        return new Color(red, green, blue);
    }

    private String applyHexColors(String message) {
        message = replaceHexPattern(message, HEX_AMPERSAND);
        message = replaceHexPattern(message, HEX_AMPERSAND_DOUBLE_HASH);
        message = replaceHexPattern(message, HEX_ANGLE_BRACKET);

        return message;
    }

    private String replaceHexPattern(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = translateHexToMinecraft(hex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String translateHexToMinecraft(String hex) {
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toLowerCase().toCharArray()) {
            builder.append("§").append(c);
        }
        return builder.toString();
    }

    private String applyLegacyColors(String message) {
        Matcher matcher = LEGACY_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase();
            matcher.appendReplacement(buffer, "§" + code);
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public String stripColors(String message) {
        if (message == null) {
            return null;
        }

        message = GRADIENT_PATTERN.matcher(message).replaceAll("$3");
        message = HEX_AMPERSAND.matcher(message).replaceAll("");
        message = HEX_AMPERSAND_DOUBLE_HASH.matcher(message).replaceAll("");
        message = HEX_ANGLE_BRACKET.matcher(message).replaceAll("");
        message = message.replaceAll("§[0-9a-fk-orxA-FK-OR]", "");
        message = LEGACY_PATTERN.matcher(message).replaceAll("");

        return message;
    }
}

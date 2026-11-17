package cz.tvojeserver.aipomocnik;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AiPomocnik extends JavaPlugin implements Listener, CommandExecutor {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("aipomocnik").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AiPomocnik byl uspesne zapnut!");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().trim();
        String lower = message.toLowerCase();

        // Kontrola, zda zpráva začíná slovem "pomocník" nebo "pomocnik"
        if (lower.startsWith("pomocník ") || lower.startsWith("pomocnik ")) {
            String question = message.substring(9).trim();
            if (question.isEmpty()) return;

            Player player = event.getPlayer();
            String apiKey = getConfig().getString("api-key", "");

            if (apiKey.isEmpty() || apiKey.equals("SEM_VLOZ_SVUJ_API_KLIC")) {
                Bukkit.getScheduler().runTask(this, () ->
                        player.sendMessage(ChatColor.RED + "[AI Pomocník] Není nastavený API klíč v config.yml!"));
                return;
            }

            // Odeslání dotazu na pozadí, aby se nesekal server
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String answer = askGemini(apiKey, player.getName(), question);
                Bukkit.getScheduler().runTask(this, () ->
                        Bukkit.broadcastMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "[AI Pomocník] " + ChatColor.WHITE + answer));
            });
        }
    }

    private String askGemini(String apiKey, String playerName, String question) {
        try {
            String prompt = "Jsi přátelský AI pomocník na českém Minecraft serveru. Odpovídej česky, stručně (maximálně 2 až 3 věty) a srozumitelně. Hráč " + playerName + " se ptá: " + question;

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);

            JsonArray partsArray = new JsonArray();
            partsArray.add(textPart);

            JsonObject contentObj = new JsonObject();
            contentObj.add("parts", partsArray);

            JsonArray contentsArray = new JsonArray();
            contentsArray.add(contentObj);

            JsonObject requestBody = new JsonObject();
            requestBody.add("contents", contentsArray);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return json.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString()
                        .replace("\n", " ")
                        .trim();
            } else {
                getLogger().warning("Chyba API (" + response.statusCode() + "): " + response.body());
                return "Omlouvám se, teď mám technický výpadek.";
            }
        } catch (Exception e) {
            getLogger().warning("Chyba při komunikaci s AI: " + e.getMessage());
            return "Nepodařilo se mi spojit s mozkem.";
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aipomocnik.admin") && sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "Nemáš oprávnění.");
            return true;
        }
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "[AI Pomocník] Konfigurace byla znovu načtena!");
        return true;
    }
}

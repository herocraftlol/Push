package com.push.plugin.chat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Les 4 messages rapides envoyes dans le chat de l'arene en cliquant sur un bloc de
 * couleur dans la hotbar pendant la pause de 3 secondes qui suit un point marque (et a
 * l'ecran de victoire) — pas le temps d'ecrire dans le chat pendant qu'on ne peut pas bouger.
 *
 * Chaque message est traduit automatiquement dans la langue de CHAQUE destinataire
 * (determinee via {@link Player#locale()}, reglee automatiquement par le client). Un
 * adversaire dont le jeu est en anglais verra donc le message en anglais, meme si
 * l'auteur a le jeu en francais, et inversement.
 */
public enum QuickMessage {

    WELL_PLAYED(Material.GREEN_CONCRETE, ChatColor.GREEN, 2, "Bien joué !", Map.ofEntries(
            Map.entry("en", "Well played!"),
            Map.entry("es", "¡Bien jugado!"),
            Map.entry("de", "Gut gespielt!"),
            Map.entry("pt", "Bem jogado!"),
            Map.entry("ru", "Отлично сыграно!"),
            Map.entry("it", "Ben giocato!"),
            Map.entry("nl", "Goed gespeeld!"),
            Map.entry("pl", "Dobra gra!"),
            Map.entry("tr", "İyi oynadın!"),
            Map.entry("zh", "打得好！"),
            Map.entry("ja", "ナイスプレー！"),
            Map.entry("ko", "잘했어요!")
    )),

    TOO_STRONG(Material.PURPLE_CONCRETE, ChatColor.DARK_PURPLE, 3, "Tu es trop fort !", Map.ofEntries(
            Map.entry("en", "You're too strong!"),
            Map.entry("es", "¡Eres demasiado fuerte!"),
            Map.entry("de", "Du bist zu stark!"),
            Map.entry("pt", "Você é forte demais!"),
            Map.entry("ru", "Ты слишком силён!"),
            Map.entry("it", "Sei troppo forte!"),
            Map.entry("nl", "Je bent te sterk!"),
            Map.entry("pl", "Jesteś zbyt silny!"),
            Map.entry("tr", "Çok güçlüsün!"),
            Map.entry("zh", "你太强了！"),
            Map.entry("ja", "強すぎる！"),
            Map.entry("ko", "너무 강해요!")
    )),

    REVENGE(Material.ORANGE_CONCRETE, ChatColor.GOLD, 5, "J'aurai ma revanche !", Map.ofEntries(
            Map.entry("en", "I'll have my revenge!"),
            Map.entry("es", "¡Tendré mi revancha!"),
            Map.entry("de", "Ich werde mich rächen!"),
            Map.entry("pt", "Terei minha revanche!"),
            Map.entry("ru", "Я отомщу!"),
            Map.entry("it", "Avrò la mia rivincita!"),
            Map.entry("nl", "Ik krijg mijn revanche!"),
            Map.entry("pl", "Wezmę odwet!"),
            Map.entry("tr", "İntikamımı alacağım!"),
            Map.entry("zh", "我会报仇的！"),
            Map.entry("ja", "リベンジしてやる！"),
            Map.entry("ko", "복수하고 말겠어!")
    )),

    NOT_SCARED(Material.BLUE_CONCRETE, ChatColor.BLUE, 6, "Même pas peur !", Map.ofEntries(
            Map.entry("en", "Not even scared!"),
            Map.entry("es", "¡Ni siquiera tengo miedo!"),
            Map.entry("de", "Nicht mal Angst!"),
            Map.entry("pt", "Nem um pouco de medo!"),
            Map.entry("ru", "Даже не страшно!"),
            Map.entry("it", "Non ho neanche paura!"),
            Map.entry("nl", "Niet eens bang!"),
            Map.entry("pl", "Wcale się nie boję!"),
            Map.entry("tr", "Hiç korkmadım!"),
            Map.entry("zh", "一点都不怕！"),
            Map.entry("ja", "全然怖くない！"),
            Map.entry("ko", "하나도 안 무서워!")
    ));

    private final Material material;
    private final ChatColor color;
    private final int hotbarSlot;
    private final String frenchText;
    private final Map<String, String> translations;

    QuickMessage(Material material, ChatColor color, int hotbarSlot, String frenchText, Map<String, String> translations) {
        this.material = material;
        this.color = color;
        this.hotbarSlot = hotbarSlot;
        this.frenchText = frenchText;
        this.translations = translations;
    }

    public Material getMaterial() {
        return material;
    }

    public ChatColor getColor() {
        return color;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    /** Texte de ce message dans la langue du destinataire (detectee via son client). */
    public String getTextFor(Player recipient) {
        String language = recipient.locale().getLanguage();
        if ("fr".equalsIgnoreCase(language)) {
            return frenchText;
        }
        String translated = translations.get(language.toLowerCase());
        if (translated != null) {
            return translated;
        }
        return translations.getOrDefault("en", frenchText);
    }

    /** Cree l'ItemStack (bloc de couleur nomme dans la langue du joueur qui le verra). */
    public ItemStack createItem(Player viewer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + "" + ChatColor.BOLD + getTextFor(viewer));
            meta.setLore(java.util.List.of(ChatColor.GRAY + getHintFor(viewer)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final Map<String, String> HINT_TRANSLATIONS = Map.ofEntries(
            Map.entry("en", "Click to send this message in the arena chat!"),
            Map.entry("es", "¡Haz clic para enviar este mensaje en el chat de la arena!"),
            Map.entry("de", "Klicke, um diese Nachricht im Arena-Chat zu senden!"),
            Map.entry("pt", "Clique para enviar esta mensagem no chat da arena!"),
            Map.entry("ru", "Нажми, чтобы отправить это сообщение в чат арены!"),
            Map.entry("it", "Clicca per inviare questo messaggio nella chat dell'arena!"),
            Map.entry("nl", "Klik om dit bericht in de arena-chat te sturen!"),
            Map.entry("pl", "Kliknij, aby wysłać tę wiadomość na czacie areny!"),
            Map.entry("tr", "Bu mesajı arena sohbetine göndermek için tıkla!"),
            Map.entry("zh", "点击将此消息发送到竞技场聊天！"),
            Map.entry("ja", "クリックしてこのメッセージをアリーナチャットに送信！"),
            Map.entry("ko", "클릭하면 이 메시지가 아레나 채팅에 전송됩니다!")
    );
    private static final String HINT_FRENCH = "Clique pour envoyer ce message dans le chat de l'arène !";

    private static String getHintFor(Player viewer) {
        String language = viewer.locale().getLanguage();
        if ("fr".equalsIgnoreCase(language)) return HINT_FRENCH;
        return HINT_TRANSLATIONS.getOrDefault(language.toLowerCase(), HINT_TRANSLATIONS.get("en"));
    }

    /** Identifie quel message correspond a cet ItemStack (via son Material). */
    public static QuickMessage fromItem(ItemStack item) {
        if (item == null) return null;
        for (QuickMessage message : values()) {
            if (message.material == item.getType()) return message;
        }
        return null;
    }
}

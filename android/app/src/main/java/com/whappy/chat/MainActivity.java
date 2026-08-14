package com.whappy.chat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final String SESSION_PREFS = "whappy_session";
    private static final int GREEN = Color.rgb(19, 215, 19);
    private static final int TEXT = Color.rgb(19, 51, 26);
    private static final int MUTED = Color.rgb(102, 126, 107);
    private static final int SURFACE = Color.rgb(245, 250, 246);
    private static final int BORDER = Color.rgb(218, 233, 220);

    private final String[] countryLabels = {
            "🇨🇬 Congo (+242)", "🇨🇩 RD Congo (+243)", "🇨🇲 Cameroun (+237)",
            "🇨🇮 Côte d’Ivoire (+225)", "🇸🇳 Sénégal (+221)", "🇫🇷 France (+33)"
    };
    private final String[] countryCodes = {"+242", "+243", "+237", "+225", "+221", "+33"};

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration liveListener;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private String pendingPhone;
    private Runnable backAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(GREEN);
        getWindow().setNavigationBarColor(Color.WHITE);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        requestNotificationPermission();
        showLaunchScreen();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) showPhoneScreen();
        else routeSignedInUser(user);
    }

    private void showLaunchScreen() {
        LinearLayout page = page(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.whappy_icon);
        page.addView(logo, sized(118, 118));
        TextView title = text("WHAPPY", 28, GREEN, true);
        title.setGravity(Gravity.CENTER);
        page.addView(title, topMargin(wrap(), 18));
        ProgressBar progress = new ProgressBar(this);
        page.addView(progress, topMargin(sized(40, 40), 24));
        setContentView(page);
    }

    private void showPhoneScreen() {
        FirebaseUser signedInUser = auth.getCurrentUser();
        if (signedInUser != null) {
            routeSignedInUser(signedInUser);
            return;
        }
        clearLiveListener();
        backAction = null;
        LinearLayout content = page(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(38), dp(24), dp(28));
        addBrand(content);
        content.addView(text("Votre numéro, votre compte", 27, TEXT, true), topMargin(wrap(), 26));
        content.addView(text("Connectez-vous une seule fois avec votre téléphone. Ensuite, WHAPPY conserve votre session sur cet appareil.", 15, MUTED, false), topMargin(matchWrap(), 8));

        LinearLayout card = card();
        content.addView(card, topMargin(matchWrap(), 24));
        card.addView(label("Pays"));
        Spinner country = new Spinner(this);
        country.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, countryLabels));
        country.setBackground(rounded(Color.WHITE, BORDER, 14));
        country.setPadding(dp(12), 0, dp(12), 0);
        card.addView(country, topMargin(matchHeight(56), 7));

        card.addView(label("Numéro de téléphone"), topMargin(matchWrap(), 17));
        EditText phone = input("06 123 45 67");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        card.addView(phone, topMargin(matchHeight(56), 7));

        TextView status = text("", 13, MUTED, false);
        card.addView(status, topMargin(matchWrap(), 10));
        Button send = primaryButton("Recevoir mon code SMS");
        card.addView(send, topMargin(matchHeight(56), 14));
        card.addView(text("La vérification est protégée par Firebase et Play Integrity sur les appareils compatibles.", 12, MUTED, false), topMargin(matchWrap(), 14));

        send.setOnClickListener(view -> {
            String countryCode = countryCodes[country.getSelectedItemPosition()];
            String formatted = PhoneNumberFormatter.normalize(countryCode, phone.getText().toString());
            if (formatted == null) {
                status.setText(countryCode.equals("+242")
                        ? "Le numéro congolais doit contenir 9 chiffres, par exemple 06 123 45 67."
                        : "Vérifiez le nombre de chiffres et l’indicatif du pays.");
                status.setTextColor(Color.rgb(180, 36, 36));
                return;
            }
            pendingPhone = formatted;
            send.setEnabled(false);
            send.setText("Vérification Android…");
            status.setText("Connexion sécurisée en cours…");
            startPhoneVerification(pendingPhone, status, send);
        });
        setScrollableContent(content);
    }

    private void startPhoneVerification(String phoneNumber, TextView status, Button button) {
        PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                status.setText("Numéro reconnu automatiquement.");
                signInWithCredential(credential, status, button);
            }

            @Override
            public void onVerificationFailed(com.google.firebase.FirebaseException exception) {
                button.setEnabled(true);
                button.setText("Réessayer");
                status.setTextColor(Color.rgb(180, 36, 36));
                String message = exception.getLocalizedMessage();
                if (exception instanceof FirebaseAuthInvalidCredentialsException) message = "Le format du numéro est incorrect.";
                status.setText(message == null ? "La vérification Android a échoué." : message);
            }

            @Override
            public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                verificationId = id;
                resendToken = token;
                showCodeScreen();
            }
        };
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void showCodeScreen() {
        backAction = this::showPhoneScreen;
        LinearLayout content = page(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(38), dp(24), dp(28));
        addBrand(content);
        content.addView(text("Code de confirmation", 27, TEXT, true), topMargin(wrap(), 28));
        content.addView(text("Saisissez le code envoyé au " + pendingPhone + ".", 15, MUTED, false), topMargin(matchWrap(), 8));

        LinearLayout card = card();
        content.addView(card, topMargin(matchWrap(), 24));
        EditText code = input("• • • • • •");
        code.setGravity(Gravity.CENTER);
        code.setTextSize(25);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        card.addView(code, matchHeight(64));
        TextView status = text("", 13, MUTED, false);
        card.addView(status, topMargin(matchWrap(), 10));
        Button verify = primaryButton("Vérifier et entrer");
        card.addView(verify, topMargin(matchHeight(56), 14));
        Button change = secondaryButton("Modifier le numéro");
        card.addView(change, topMargin(matchHeight(50), 10));
        change.setOnClickListener(view -> showPhoneScreen());
        verify.setOnClickListener(view -> {
            String value = code.getText().toString().replaceAll("\\D", "");
            if (verificationId == null || value.length() != 6) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Entrez les 6 chiffres reçus par SMS.");
                return;
            }
            verify.setEnabled(false);
            verify.setText("Connexion…");
            signInWithCredential(PhoneAuthProvider.getCredential(verificationId, value), status, verify);
        });
        setScrollableContent(content);
    }

    private void signInWithCredential(PhoneAuthCredential credential, TextView status, Button button) {
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult().getUser() != null) {
                routeSignedInUser(task.getResult().getUser());
                return;
            }
            button.setEnabled(true);
            button.setText("Réessayer");
            status.setTextColor(Color.rgb(180, 36, 36));
            status.setText("Le code est incorrect ou a expiré.");
        });
    }

    private void routeSignedInUser(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
            String displayName = stringValue(snapshot.get("displayName"), stringValue(user.getDisplayName(), ""));
            if (displayName.length() < 2) {
                String cached = cachedDisplayName(user);
                if (cached.length() >= 2) showHome(user, cached);
                else showProfileScreen(user);
                return;
            }
            cacheDisplayName(user, displayName);
            showHome(user, displayName);
        }).addOnFailureListener(error -> showHome(user, cachedDisplayName(user)));
    }

    private void showProfileScreen(FirebaseUser user) {
        backAction = null;
        LinearLayout content = page(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(38), dp(24), dp(28));
        addBrand(content);
        content.addView(text("Créez votre profil", 27, TEXT, true), topMargin(wrap(), 28));
        content.addView(text("Ce nom sera visible par vos contacts WHAPPY.", 15, MUTED, false), topMargin(matchWrap(), 8));
        LinearLayout card = card();
        content.addView(card, topMargin(matchWrap(), 24));
        EditText name = input("Votre nom complet");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        card.addView(name, matchHeight(56));
        TextView status = text("", 13, MUTED, false);
        card.addView(status, topMargin(matchWrap(), 10));
        Button save = primaryButton("Créer mon profil");
        card.addView(save, topMargin(matchHeight(56), 14));
        save.setOnClickListener(view -> {
            String value = name.getText().toString().trim();
            if (value.length() < 2) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Entrez au moins deux caractères.");
                return;
            }
            save.setEnabled(false);
            Map<String, Object> profile = new HashMap<>();
            profile.put("displayName", value);
            profile.put("phoneNumber", user.getPhoneNumber() == null ? pendingPhone : user.getPhoneNumber());
            profile.put("updatedAt", FieldValue.serverTimestamp());
            db.collection("users").document(user.getUid()).set(profile, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        cacheDisplayName(user, value);
                        showHome(user, value);
                    })
                    .addOnFailureListener(error -> {
                        save.setEnabled(true);
                        status.setTextColor(Color.rgb(180, 36, 36));
                        status.setText("Impossible d’enregistrer le profil.");
                    });
        });
        setScrollableContent(content);
    }

    private void showHome(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = null;
        LinearLayout page = page(Gravity.TOP);
        page.setPadding(dp(18), dp(14), dp(18), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.whappy_icon);
        header.addView(logo, sized(48, 48));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("WHAPPY", 21, GREEN, true));
        titles.addView(text("Bonjour " + displayName + " · session active", 12, MUTED, false));
        header.addView(titles, weighted(1));
        TextView avatar = text(initials(displayName), 15, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(GREEN, GREEN, 22));
        header.addView(avatar, sized(44, 44));
        avatar.setOnClickListener(view -> showNativeProfile(user, displayName));
        page.addView(header, matchWrap());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hero = card();
        hero.setBackground(rounded(Color.rgb(232, 255, 233), GREEN, 22));
        hero.addView(text("Tout WHAPPY est ici", 23, TEXT, true));
        hero.addView(text("Messages, groupes, appels, directs, marché et activité professionnelle dans une seule application.", 14, MUTED, false), topMargin(matchWrap(), 6));
        content.addView(hero, topMargin(matchWrap(), 20));

        content.addView(text("Espaces", 20, TEXT, true), topMargin(matchWrap(), 22));
        content.addView(actionRow(
                actionButton("💬\nMessages", view -> showFindContact(user, displayName)),
                actionButton("👥\nGroupes", view -> showGroups(user, displayName))), topMargin(matchHeight(86), 10));
        content.addView(actionRow(
                actionButton("☎\nAppels", view -> showCallsInfo(user, displayName)),
                actionButton("●\nDirects", view -> showDirects(user, displayName))), topMargin(matchHeight(86), 9));
        content.addView(actionRow(
                actionButton("🛍\nMarché", view -> showMarket(user, displayName)),
                actionButton("▣\nBusiness", view -> showBusiness(user, displayName))), topMargin(matchHeight(86), 9));

        LinearLayout messageHeader = new LinearLayout(this);
        messageHeader.setGravity(Gravity.CENTER_VERTICAL);
        messageHeader.addView(text("Conversations", 20, TEXT, true), weighted(1));
        Button newChat = smallButton("＋ Nouveau");
        newChat.setTextSize(14);
        messageHeader.addView(newChat, wrap());
        content.addView(messageHeader, topMargin(matchWrap(), 24));
        content.addView(text("Synchronisées en temps réel", 13, MUTED, false), topMargin(matchWrap(), 3));
        newChat.setOnClickListener(view -> showFindContact(user, displayName));

        LinearLayout conversations = new LinearLayout(this);
        conversations.setOrientation(LinearLayout.VERTICAL);
        conversations.addView(text("Chargement des conversations…", 14, MUTED, false), topMargin(matchWrap(), 14));
        content.addView(conversations, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, matchWrap());
        page.addView(scroll, weighted(1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(rounded(Color.WHITE, BORDER, 18));
        String[] tabs = {"⌂ Accueil", "💬 Message", "▣ Business", "☺ Profil"};
        for (String tab : tabs) {
            Button item = smallButton(tab);
            if (!tab.contains("Accueil")) item.setTextColor(MUTED);
            nav.addView(item, weighted(1));
            if (tab.contains("Message")) item.setOnClickListener(view -> showFindContact(user, displayName));
            if (tab.contains("Business")) item.setOnClickListener(view -> showBusiness(user, displayName));
            if (tab.contains("Profil")) item.setOnClickListener(view -> showNativeProfile(user, displayName));
        }
        page.addView(nav, topMargin(matchHeight(58), 10));
        setContentView(page);

        liveListener = db.collection("conversations").whereArrayContains("memberIds", user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        conversations.removeAllViews();
                        conversations.addView(text("Impossible de charger les conversations.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
                    documents.sort((left, right) -> Long.compare(timestampMillis(right), timestampMillis(left)));
                    renderConversations(conversations, documents, user, displayName);
                });
    }

    private void renderConversations(LinearLayout container, List<DocumentSnapshot> documents, FirebaseUser user, String displayName) {
        container.removeAllViews();
        if (documents.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Aucune discussion", 18, TEXT, true));
            empty.addView(text("Touchez « Nouvelle discussion » et recherchez un contact par son numéro.", 14, MUTED, false), topMargin(matchWrap(), 7));
            container.addView(empty, topMargin(matchWrap(), 18));
            return;
        }
        for (DocumentSnapshot document : documents) {
            Map<String, Object> peer = peerFrom(document, user.getUid());
            if (peer == null) continue;
            String peerName = stringValue(peer.get("displayName"), "Contact WHAPPY");
            String peerPhone = stringValue(peer.get("phoneNumber"), "");
            LinearLayout row = card();
            row.setPadding(dp(16), dp(15), dp(16), dp(15));
            TextView avatar = text(initials(peerName), 17, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(rounded(GREEN, GREEN, 24));
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.addView(text(peerName, 17, TEXT, true));
            body.addView(text(stringValue(document.get("lastMessage"), "Commencez la conversation"), 13, MUTED, false), topMargin(matchWrap(), 3));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(avatar, sized(48, 48));
            row.addView(body, leftWeighted(1, 13));
            row.addView(text("›", 28, GREEN, false));
            row.setOnClickListener(view -> openChat(document.getId(), peerName, peerPhone, user, displayName));
            container.addView(row, topMargin(matchWrap(), 10));
        }
    }

    private void showFindContact(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Nouvelle discussion", backAction);
        content.addView(text("Rechercher avec le numéro complet", 15, MUTED, false), topMargin(matchWrap(), 22));
        EditText phone = input("+242 06 123 45 67");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(phone, topMargin(matchHeight(58), 8));
        TextView status = text("", 14, MUTED, false);
        content.addView(status, topMargin(matchWrap(), 12));
        Button find = primaryButton("Trouver ce contact");
        content.addView(find, topMargin(matchHeight(54), 12));
        find.setOnClickListener(view -> {
            String normalized = PhoneNumberFormatter.normalize("+242", phone.getText().toString());
            if (normalized == null) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Entrez le numéro avec l’indicatif du pays.");
                return;
            }
            find.setEnabled(false);
            status.setTextColor(MUTED);
            status.setText("Recherche…");
            db.collection("users").whereEqualTo("phoneNumber", normalized).limit(1).get().addOnSuccessListener(result -> {
                if (result.isEmpty()) {
                    find.setEnabled(true);
                    status.setTextColor(Color.rgb(180, 36, 36));
                    status.setText("Ce numéro n’a pas encore de compte WHAPPY.");
                    return;
                }
                DocumentSnapshot peerDoc = result.getDocuments().get(0);
                if (peerDoc.getId().equals(user.getUid())) {
                    find.setEnabled(true);
                    status.setText("C’est votre propre numéro.");
                    return;
                }
                db.collection("users").document(user.getUid()).get().addOnSuccessListener(me -> createOrOpenConversation(user, displayName, me, peerDoc));
            }).addOnFailureListener(error -> {
                find.setEnabled(true);
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Recherche indisponible.");
            });
        });
        setScrollableContent(content);
    }

    private void createOrOpenConversation(FirebaseUser user, String displayName, DocumentSnapshot me, DocumentSnapshot peer) {
        List<String> ids = new ArrayList<>();
        ids.add(user.getUid());
        ids.add(peer.getId());
        Collections.sort(ids);
        String conversationId = "direct-" + ids.get(0) + "-" + ids.get(1);
        String peerName = stringValue(peer.get("displayName"), "Contact WHAPPY");
        String peerPhone = stringValue(peer.get("phoneNumber"), "");
        Map<String, Object> mine = new HashMap<>();
        mine.put("uid", user.getUid());
        mine.put("displayName", displayName);
        mine.put("phoneNumber", user.getPhoneNumber() == null ? stringValue(me.get("phoneNumber"), "") : user.getPhoneNumber());
        Map<String, Object> theirs = new HashMap<>();
        theirs.put("uid", peer.getId());
        theirs.put("displayName", peerName);
        theirs.put("phoneNumber", peerPhone);
        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", user.getUid());
        data.put("memberIds", ids);
        data.put("members", java.util.Arrays.asList(mine, theirs));
        data.put("typingBy", new HashMap<String, Object>());
        data.put("readBy", new HashMap<String, Object>());
        data.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("conversations").document(conversationId).get().addOnSuccessListener(existing -> {
            if (existing.exists()) {
                openChat(conversationId, peerName, peerPhone, user, displayName);
                return;
            }
            db.collection("conversations").document(conversationId).set(data)
                    .addOnSuccessListener(unused -> openChat(conversationId, peerName, peerPhone, user, displayName))
                    .addOnFailureListener(error -> Toast.makeText(this, "Impossible de créer la discussion", Toast.LENGTH_LONG).show());
        });
    }

    private void openChat(String conversationId, String peerName, String peerPhone, FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout page = page(Gravity.TOP);
        page.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = smallButton("‹");
        back.setTextSize(28);
        header.addView(back, sized(48, 48));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(text(peerName, 18, TEXT, true));
        identity.addView(text(peerPhone.isEmpty() ? "Contact WHAPPY" : peerPhone, 12, MUTED, false));
        header.addView(identity, weighted(1));
        Button dial = smallButton("☎");
        header.addView(dial, sized(48, 48));
        back.setOnClickListener(view -> showHome(user, displayName));
        dial.setOnClickListener(view -> {
            if (peerPhone.isEmpty()) Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show();
            else startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + peerPhone)));
        });
        page.addView(header, matchWrap());

        LinearLayout messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(3), dp(14), dp(3), dp(14));
        ScrollView messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.addView(messageList, matchWrap());
        page.addView(messagesScroll, weighted(1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        EditText input = input("Votre message…");
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, weightedHeight(1, 54));
        Button send = primaryButton("➤");
        send.setTextSize(20);
        composer.addView(send, leftSized(56, 54, 8));
        page.addView(composer, matchWrap());
        setContentView(page);

        liveListener = db.collection("conversations").document(conversationId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    messageList.removeAllViews();
                    if (error != null || snapshot == null) {
                        messageList.addView(text("Messages indisponibles.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    for (DocumentSnapshot message : snapshot.getDocuments()) {
                        boolean mine = user.getUid().equals(message.getString("senderId"));
                        TextView bubble = text(stringValue(message.get("text"), ""), 15, mine ? Color.WHITE : TEXT, false);
                        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
                        bubble.setBackground(rounded(mine ? GREEN : Color.WHITE, mine ? GREEN : BORDER, 18));
                        LinearLayout line = new LinearLayout(this);
                        line.setGravity(mine ? Gravity.END : Gravity.START);
                        line.addView(bubble, new LinearLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * .76f), ViewGroup.LayoutParams.WRAP_CONTENT));
                        messageList.addView(line, topMargin(matchWrap(), 7));
                    }
                    messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
                });

        View.OnClickListener sendAction = view -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            if (value.length() > 4000) {
                Toast.makeText(this, "Message trop long", Toast.LENGTH_SHORT).show();
                return;
            }
            send.setEnabled(false);
            Map<String, Object> message = new HashMap<>();
            message.put("text", value);
            message.put("senderId", user.getUid());
            message.put("createdAt", FieldValue.serverTimestamp());
            db.collection("conversations").document(conversationId).collection("messages").add(message)
                    .addOnSuccessListener(reference -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("lastMessage", value);
                        update.put("updatedAt", FieldValue.serverTimestamp());
                        update.put("typingBy." + user.getUid(), false);
                        db.collection("conversations").document(conversationId).update(update);
                        input.setText("");
                        send.setEnabled(true);
                    }).addOnFailureListener(error -> {
                        send.setEnabled(true);
                        Toast.makeText(this, "Envoi impossible", Toast.LENGTH_SHORT).show();
                    });
        };
        send.setOnClickListener(sendAction);
        input.setOnEditorActionListener((view, actionId, event) -> {
            sendAction.onClick(view);
            return true;
        });
    }

    private void showCallsInfo(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Appels", backAction);
        content.addView(text("Appelez vos contacts", 22, TEXT, true), topMargin(matchWrap(), 22));
        content.addView(text("WHAPPY ouvre directement le composeur Android avec le bon numéro.", 14, MUTED, false), topMargin(matchWrap(), 5));
        LinearLayout calls = new LinearLayout(this);
        calls.setOrientation(LinearLayout.VERTICAL);
        calls.addView(text("Chargement des contacts…", 14, MUTED, false), topMargin(matchWrap(), 18));
        content.addView(calls, matchWrap());
        setContentView(content);

        liveListener = db.collection("conversations").whereArrayContains("memberIds", user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    calls.removeAllViews();
                    if (error != null || snapshot == null) {
                        calls.addView(text("Contacts indisponibles pour le moment.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    if (snapshot.isEmpty()) {
                        LinearLayout empty = card();
                        empty.addView(text("Aucun contact récent", 18, TEXT, true));
                        empty.addView(text("Créez d’abord une discussion depuis l’accueil.", 14, MUTED, false), topMargin(matchWrap(), 6));
                        calls.addView(empty, topMargin(matchWrap(), 18));
                        return;
                    }
                    for (DocumentSnapshot conversation : snapshot.getDocuments()) {
                        Map<String, Object> peer = peerFrom(conversation, user.getUid());
                        if (peer == null) continue;
                        String name = stringValue(peer.get("displayName"), "Contact WHAPPY");
                        String phone = stringValue(peer.get("phoneNumber"), "");
                        LinearLayout item = card();
                        item.addView(text("☎  " + name, 18, TEXT, true));
                        item.addView(text(phone.isEmpty() ? "Numéro indisponible" : phone, 13, MUTED, false), topMargin(matchWrap(), 4));
                        item.setOnClickListener(view -> {
                            if (phone.isEmpty()) Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show();
                            else startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                        });
                        calls.addView(item, topMargin(matchWrap(), 10));
                    }
                });
    }

    private void showBusiness(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Business", backAction);
        LinearLayout intro = card();
        intro.setBackground(rounded(Color.rgb(232, 255, 233), GREEN, 22));
        intro.addView(text("WHAPPY Business", 22, TEXT, true));
        intro.addView(text("Créez une page professionnelle ou créateur. Elle est synchronisée avec votre compte.", 14, MUTED, false), topMargin(matchWrap(), 6));
        Button create = primaryButton("＋ Créer une page");
        intro.addView(create, topMargin(matchHeight(52), 14));
        content.addView(intro, topMargin(matchWrap(), 20));
        content.addView(text("Mes pages", 20, TEXT, true), topMargin(matchWrap(), 22));
        LinearLayout pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        pages.addView(text("Chargement…", 14, MUTED, false), topMargin(matchWrap(), 12));
        content.addView(pages, matchWrap());
        create.setOnClickListener(view -> showCreateBusinessPage(user, displayName));
        setContentView(content);

        liveListener = db.collection("businessPages").whereEqualTo("ownerId", user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    pages.removeAllViews();
                    if (error != null || snapshot == null) {
                        pages.addView(text("Pages indisponibles.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    if (snapshot.isEmpty()) {
                        pages.addView(text("Vous n’avez pas encore créé de page.", 14, MUTED, false), topMargin(matchWrap(), 12));
                        return;
                    }
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        LinearLayout item = card();
                        item.addView(text("▣  " + stringValue(document.get("name"), "Page WHAPPY"), 18, TEXT, true));
                        item.addView(text("@" + stringValue(document.get("handle"), "whappy") + " · " + stringValue(document.get("category"), "Professionnel"), 13, MUTED, false), topMargin(matchWrap(), 5));
                        item.addView(text("● Page active", 12, GREEN, true), topMargin(matchWrap(), 9));
                        pages.addView(item, topMargin(matchWrap(), 10));
                    }
                });
    }

    private void showCreateBusinessPage(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showBusiness(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Nouvelle page", backAction);
        EditText name = input("Nom de la page");
        EditText category = input("Catégorie · ex. Mode, Média, Restaurant");
        EditText bio = input("Présentation courte");
        content.addView(label("Nom public"), topMargin(matchWrap(), 22));
        content.addView(name, topMargin(matchHeight(56), 7));
        content.addView(label("Catégorie"), topMargin(matchWrap(), 16));
        content.addView(category, topMargin(matchHeight(56), 7));
        content.addView(label("Bio"), topMargin(matchWrap(), 16));
        content.addView(bio, topMargin(matchHeight(56), 7));
        TextView status = text("", 13, MUTED, false);
        content.addView(status, topMargin(matchWrap(), 10));
        Button publish = primaryButton("Créer ma page Business");
        content.addView(publish, topMargin(matchHeight(56), 14));
        publish.setOnClickListener(view -> {
            String pageName = name.getText().toString().trim();
            String pageCategory = category.getText().toString().trim();
            String pageBio = bio.getText().toString().trim();
            if (pageName.length() < 2 || pageBio.length() > 400) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Ajoutez un nom valide et une bio de 400 caractères maximum.");
                return;
            }
            String handle = pageName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (handle.length() < 3) handle = "whappy" + System.currentTimeMillis() % 100000;
            if (handle.length() > 50) handle = handle.substring(0, 50);
            Map<String, Object> data = new HashMap<>();
            data.put("ownerId", user.getUid());
            data.put("name", pageName);
            data.put("handle", handle);
            data.put("type", "business");
            data.put("category", pageCategory.isEmpty() ? "Professionnel" : pageCategory);
            data.put("bio", pageBio);
            data.put("status", "active");
            data.put("followers", 0);
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            publish.setEnabled(false);
            db.collection("businessPages").add(data)
                    .addOnSuccessListener(reference -> showBusiness(user, displayName))
                    .addOnFailureListener(error -> {
                        publish.setEnabled(true);
                        status.setTextColor(Color.rgb(180, 36, 36));
                        status.setText("La page n’a pas pu être créée.");
                    });
        });
        setScrollableContent(content);
    }

    private void showGroups(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Groupes", backAction);
        Button create = primaryButton("＋ Créer un groupe");
        content.addView(create, topMargin(matchHeight(54), 20));
        LinearLayout groups = new LinearLayout(this);
        groups.setOrientation(LinearLayout.VERTICAL);
        groups.addView(text("Chargement des groupes…", 14, MUTED, false), topMargin(matchWrap(), 18));
        content.addView(groups, matchWrap());
        create.setOnClickListener(view -> showCreateGroup(user, displayName));
        setContentView(content);

        liveListener = db.collection("groups").whereArrayContains("memberIds", user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    groups.removeAllViews();
                    if (error != null || snapshot == null) {
                        groups.addView(text("Groupes indisponibles.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    if (snapshot.isEmpty()) {
                        LinearLayout empty = card();
                        empty.addView(text("Créez votre premier groupe", 18, TEXT, true));
                        empty.addView(text("Famille, équipe, communauté ou projet.", 14, MUTED, false), topMargin(matchWrap(), 6));
                        groups.addView(empty, topMargin(matchWrap(), 18));
                        return;
                    }
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String groupName = stringValue(document.get("name"), "Groupe WHAPPY");
                        LinearLayout item = card();
                        item.addView(text("👥  " + groupName, 18, TEXT, true));
                        Object rawMembers = document.get("memberIds");
                        int members = rawMembers instanceof List ? ((List<?>) rawMembers).size() : 1;
                        item.addView(text(members + (members > 1 ? " membres" : " membre") + " · " + stringValue(document.get("lastMessage"), "Nouvelle communauté"), 13, MUTED, false), topMargin(matchWrap(), 5));
                        item.setOnClickListener(view -> openGroupChat(document.getId(), groupName, user, displayName));
                        groups.addView(item, topMargin(matchWrap(), 10));
                    }
                });
    }

    private void showCreateGroup(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showGroups(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Créer un groupe", backAction);
        content.addView(text("Votre communauté WHAPPY", 22, TEXT, true), topMargin(matchWrap(), 24));
        EditText name = input("Nom du groupe");
        content.addView(name, topMargin(matchHeight(58), 14));
        TextView status = text("", 13, MUTED, false);
        content.addView(status, topMargin(matchWrap(), 10));
        Button create = primaryButton("Créer et ouvrir le groupe");
        content.addView(create, topMargin(matchHeight(56), 14));
        create.setOnClickListener(view -> {
            String groupName = name.getText().toString().trim();
            if (groupName.length() < 2 || groupName.length() > 80) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Le nom doit contenir entre 2 et 80 caractères.");
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("ownerId", user.getUid());
            data.put("name", groupName);
            data.put("memberIds", Collections.singletonList(user.getUid()));
            data.put("memberNames", Collections.singletonList(displayName));
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            create.setEnabled(false);
            db.collection("groups").add(data)
                    .addOnSuccessListener(reference -> openGroupChat(reference.getId(), groupName, user, displayName))
                    .addOnFailureListener(error -> {
                        create.setEnabled(true);
                        status.setTextColor(Color.rgb(180, 36, 36));
                        status.setText("Création impossible.");
                    });
        });
        setContentView(content);
    }

    private void openGroupChat(String groupId, String groupName, FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showGroups(user, displayName);
        LinearLayout page = page(Gravity.TOP);
        page.setPadding(dp(14), dp(12), dp(14), dp(12));
        addBackHeader(page, groupName, backAction);
        LinearLayout messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(messageList, matchWrap());
        page.addView(scroll, weighted(1));
        LinearLayout composer = new LinearLayout(this);
        EditText input = input("Message au groupe…");
        composer.addView(input, weightedHeight(1, 54));
        Button send = primaryButton("➤");
        composer.addView(send, leftSized(56, 54, 8));
        page.addView(composer, matchWrap());
        setContentView(page);

        liveListener = db.collection("groups").document(groupId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    messageList.removeAllViews();
                    if (error != null || snapshot == null) {
                        messageList.addView(text("Messages indisponibles.", 14, Color.rgb(180, 36, 36), false));
                        return;
                    }
                    for (DocumentSnapshot message : snapshot.getDocuments()) {
                        boolean mine = user.getUid().equals(message.getString("senderId"));
                        LinearLayout bubble = card();
                        bubble.setBackground(rounded(mine ? Color.rgb(232, 255, 233) : Color.WHITE, mine ? GREEN : BORDER, 18));
                        bubble.addView(text(stringValue(message.get("senderName"), "Membre"), 12, GREEN, true));
                        bubble.addView(text(stringValue(message.get("text"), ""), 15, TEXT, false), topMargin(matchWrap(), 4));
                        messageList.addView(bubble, topMargin(matchWrap(), 8));
                    }
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
        View.OnClickListener sendAction = view -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty() || value.length() > 4000) return;
            Map<String, Object> message = new HashMap<>();
            message.put("senderId", user.getUid());
            message.put("senderName", displayName);
            message.put("text", value);
            message.put("createdAt", FieldValue.serverTimestamp());
            send.setEnabled(false);
            db.collection("groups").document(groupId).collection("messages").add(message)
                    .addOnSuccessListener(reference -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("lastMessage", value);
                        update.put("updatedAt", FieldValue.serverTimestamp());
                        db.collection("groups").document(groupId).update(update);
                        input.setText("");
                        send.setEnabled(true);
                    }).addOnFailureListener(error -> {
                        send.setEnabled(true);
                        Toast.makeText(this, "Envoi impossible", Toast.LENGTH_SHORT).show();
                    });
        };
        send.setOnClickListener(sendAction);
        input.setOnEditorActionListener((view, actionId, event) -> { sendAction.onClick(view); return true; });
    }

    private void showMarket(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Marché WHAPPY", backAction);
        Button publish = primaryButton("＋ Publier une annonce");
        content.addView(publish, topMargin(matchHeight(54), 20));
        LinearLayout listings = new LinearLayout(this);
        listings.setOrientation(LinearLayout.VERTICAL);
        listings.addView(text("Chargement du marché…", 14, MUTED, false), topMargin(matchWrap(), 18));
        content.addView(listings, matchWrap());
        publish.setOnClickListener(view -> showCreateListing(user, displayName));
        setContentView(content);

        liveListener = db.collection("listings").addSnapshotListener((snapshot, error) -> {
            listings.removeAllViews();
            if (error != null || snapshot == null) {
                listings.addView(text("Marché indisponible.", 14, Color.rgb(180, 36, 36), false));
                return;
            }
            List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
            documents.sort((left, right) -> Long.compare(timestampMillis(right), timestampMillis(left)));
            if (documents.isEmpty()) listings.addView(text("Soyez le premier à publier une annonce.", 14, MUTED, false), topMargin(matchWrap(), 18));
            for (DocumentSnapshot document : documents) {
                LinearLayout item = card();
                item.addView(text(stringValue(document.get("title"), "Annonce WHAPPY"), 18, TEXT, true));
                item.addView(text(stringValue(document.get("price"), "Prix à discuter") + " · " + stringValue(document.get("place"), "WHAPPY"), 14, GREEN, true), topMargin(matchWrap(), 6));
                item.addView(text(user.getUid().equals(document.getString("ownerId")) ? "Votre annonce" : "Annonce de la communauté", 12, MUTED, false), topMargin(matchWrap(), 7));
                listings.addView(item, topMargin(matchWrap(), 10));
            }
        });
    }

    private void showCreateListing(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showMarket(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Nouvelle annonce", backAction);
        EditText title = input("Que proposez-vous ?");
        EditText price = input("Prix · ex. 25 000 FCFA");
        EditText place = input("Lieu · ex. Brazzaville");
        content.addView(title, topMargin(matchHeight(56), 22));
        content.addView(price, topMargin(matchHeight(56), 12));
        content.addView(place, topMargin(matchHeight(56), 12));
        TextView status = text("", 13, MUTED, false);
        content.addView(status, topMargin(matchWrap(), 10));
        Button publish = primaryButton("Publier sur WHAPPY");
        content.addView(publish, topMargin(matchHeight(56), 14));
        publish.setOnClickListener(view -> {
            String itemTitle = title.getText().toString().trim();
            if (itemTitle.length() < 2 || itemTitle.length() > 120) {
                status.setTextColor(Color.rgb(180, 36, 36));
                status.setText("Le titre doit contenir entre 2 et 120 caractères.");
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("ownerId", user.getUid());
            data.put("title", itemTitle);
            data.put("price", stringValue(price.getText(), "Prix à discuter"));
            data.put("place", stringValue(place.getText(), "Brazzaville"));
            data.put("seller", displayName);
            data.put("category", "Communauté");
            data.put("mode", "vente");
            data.put("status", "active");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            publish.setEnabled(false);
            db.collection("listings").add(data)
                    .addOnSuccessListener(reference -> showMarket(user, displayName))
                    .addOnFailureListener(error -> {
                        publish.setEnabled(true);
                        status.setTextColor(Color.rgb(180, 36, 36));
                        status.setText("Publication impossible.");
                    });
        });
        setScrollableContent(content);
    }

    private void showDirects(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Studio Direct", backAction);
        LinearLayout studio = card();
        studio.setBackground(rounded(Color.rgb(19, 51, 26), Color.rgb(19, 51, 26), 22));
        studio.addView(text("●  WHAPPY DIRECT", 13, GREEN, true));
        studio.addView(text("Créez votre contenu depuis Android", 23, Color.WHITE, true), topMargin(matchWrap(), 10));
        studio.addView(text("Enregistrez une vidéo avec la caméra native, puis partagez-la avec votre communauté.", 14, Color.rgb(211, 230, 215), false), topMargin(matchWrap(), 7));
        Button camera = primaryButton("🎥 Ouvrir la caméra");
        studio.addView(camera, topMargin(matchHeight(54), 18));
        content.addView(studio, topMargin(matchWrap(), 22));
        Button share = secondaryButton("Partager une invitation WHAPPY");
        content.addView(share, topMargin(matchHeight(54), 14));
        camera.setOnClickListener(view -> {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) == null) Toast.makeText(this, "Caméra indisponible", Toast.LENGTH_SHORT).show();
            else startActivity(intent);
        });
        share.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, displayName + " vous invite sur WHAPPY.");
            startActivity(Intent.createChooser(intent, "Partager avec…"));
        });
        setContentView(content);
    }

    private void showNativeProfile(FirebaseUser user, String displayName) {
        clearLiveListener();
        backAction = () -> showHome(user, displayName);
        LinearLayout content = page(Gravity.TOP);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        addBackHeader(content, "Profil", backAction);
        LinearLayout card = card();
        TextView avatar = text(initials(displayName), 28, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(GREEN, GREEN, 40));
        card.addView(avatar, sized(80, 80));
        card.addView(text(displayName, 22, TEXT, true), topMargin(matchWrap(), 16));
        card.addView(text(user.getPhoneNumber() == null ? "Compte WHAPPY" : user.getPhoneNumber(), 14, MUTED, false), topMargin(matchWrap(), 4));
        card.addView(text("● Session conservée sur cet appareil", 13, GREEN, true), topMargin(matchWrap(), 14));
        content.addView(card, topMargin(matchWrap(), 24));
        LinearLayout security = card();
        security.addView(text("Compte et sécurité", 18, TEXT, true));
        security.addView(text("WHAPPY vous reconnecte automatiquement. Vous ne ressaisissez le numéro qu’après une déconnexion volontaire.", 14, MUTED, false), topMargin(matchWrap(), 7));
        Button logout = secondaryButton("Se déconnecter de cet appareil");
        logout.setTextColor(Color.rgb(170, 32, 32));
        security.addView(logout, topMargin(matchHeight(52), 16));
        content.addView(security, topMargin(matchWrap(), 14));
        logout.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Se déconnecter ?")
                .setMessage("Votre numéro sera redemandé uniquement si vous confirmez cette déconnexion.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Se déconnecter", (dialog, which) -> {
                    clearLiveListener();
                    auth.signOut();
                    showPhoneScreen();
                })
                .show());
        setContentView(content);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> peerFrom(DocumentSnapshot document, String uid) {
        Object raw = document.get("members");
        if (!(raw instanceof List)) return null;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> member = (Map<String, Object>) item;
            if (!uid.equals(String.valueOf(member.get("uid")))) return member;
        }
        return null;
    }

    private long timestampMillis(DocumentSnapshot document) {
        Timestamp value = document.getTimestamp("updatedAt");
        return value == null ? 0L : value.toDate().getTime();
    }

    private void cacheDisplayName(FirebaseUser user, String displayName) {
        getSharedPreferences(SESSION_PREFS, MODE_PRIVATE).edit()
                .putString("display_name_" + user.getUid(), displayName.trim())
                .apply();
    }

    private String cachedDisplayName(FirebaseUser user) {
        String firebaseName = stringValue(user.getDisplayName(), "");
        if (firebaseName.length() >= 2) return firebaseName;
        String cached = getSharedPreferences(SESSION_PREFS, MODE_PRIVATE)
                .getString("display_name_" + user.getUid(), "");
        if (cached != null && cached.trim().length() >= 2) return cached.trim();
        String phone = stringValue(user.getPhoneNumber(), "");
        return phone.isEmpty() ? "Membre WHAPPY" : "Membre " + phone.substring(Math.max(0, phone.length() - 4));
    }

    private LinearLayout actionRow(Button left, Button right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weightedHeight(1, 86));
        row.addView(right, leftWeightedHeight(1, 86, 9));
        return row;
    }

    private Button actionButton(String value, View.OnClickListener listener) {
        Button button = secondaryButton(value);
        button.setTextColor(TEXT);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    private void addBrand(LinearLayout parent) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.whappy_icon);
        parent.addView(logo, sized(76, 76));
        TextView wordmark = text("WHAPPY", 21, GREEN, true);
        wordmark.setGravity(Gravity.CENTER);
        parent.addView(wordmark, topMargin(wrap(), 10));
    }

    private void addBackHeader(LinearLayout parent, String title, Runnable action) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = smallButton("‹");
        back.setTextSize(28);
        header.addView(back, sized(48, 48));
        header.addView(text(title, 23, TEXT, true), leftWeighted(1, 10));
        back.setOnClickListener(view -> action.run());
        parent.addView(header, matchWrap());
    }

    private LinearLayout page(int gravity) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(gravity);
        page.setBackgroundColor(SURFACE);
        return page;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, BORDER, 22));
        return card;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(0f, 1.12f);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private TextView label(String value) {
        return text(value, 13, TEXT, true);
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(150, 164, 153));
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(15), 0, dp(15), 0);
        input.setBackground(rounded(Color.WHITE, BORDER, 14));
        return input;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(GREEN, GREEN, 15));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = primaryButton(value);
        button.setTextColor(GREEN);
        button.setBackground(rounded(Color.WHITE, BORDER, 15));
        return button;
    }

    private Button smallButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setTextColor(GREEN);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private void setScrollableContent(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private String initials(String name) {
        String[] pieces = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) if (!piece.isEmpty() && result.length() < 2) result.append(piece.substring(0, 1).toUpperCase(Locale.ROOT));
        return result.length() == 0 ? "W" : result.toString();
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? fallback : result;
    }

    private void clearLiveListener() {
        if (liveListener != null) liveListener.remove();
        liveListener = null;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
    }

    @Override
    public void onBackPressed() {
        if (backAction != null) backAction.run();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        clearLiveListener();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
    }

    private LinearLayout.LayoutParams sized(int width, int height) {
        return new LinearLayout.LayoutParams(dp(width), dp(height));
    }

    private LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private LinearLayout.LayoutParams weightedHeight(float weight, int height) {
        return new LinearLayout.LayoutParams(0, dp(height), weight);
    }

    private LinearLayout.LayoutParams leftWeighted(float weight, int margin) {
        LinearLayout.LayoutParams params = weighted(weight);
        params.leftMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams leftSized(int width, int height, int margin) {
        LinearLayout.LayoutParams params = sized(width, height);
        params.leftMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams leftWeightedHeight(float weight, int height, int margin) {
        LinearLayout.LayoutParams params = weightedHeight(weight, height);
        params.leftMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int margin) {
        params.topMargin = dp(margin);
        return params;
    }
}

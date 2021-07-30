package rocks.spaghetti.headlessmc.game.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import net.minecraft.client.util.NetworkUtils;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.encryption.NetworkEncryptionException;
import net.minecraft.network.encryption.NetworkEncryptionUtils;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.s2c.login.*;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import rocks.spaghetti.headlessmc.game.GameClient;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.function.Consumer;

public class LoginNetworkHandler implements ClientLoginPacketListener {
    private static final Logger LOGGER = LogManager.getLogger();

    private final GameClient client;
    private GameProfile profile;
    private Consumer<Text> statusConsumer;

    public LoginNetworkHandler(GameClient client, Consumer<Text> statusConsumer) {
        this.client = client;
        this.statusConsumer = statusConsumer;
    }

    public LoginNetworkHandler(GameClient client) {
        this(client, text -> LOGGER.info(text.getString()));
    }

    @Override
    public void onHello(LoginHelloS2CPacket packet) {
        Cipher decrypt;
        Cipher encrypt;
        String serverId;
        LoginKeyC2SPacket loginPacket;
        try {
            SecretKey secretKey = NetworkEncryptionUtils.generateKey();
            PublicKey publicKey = packet.getPublicKey();
            serverId = (new BigInteger(NetworkEncryptionUtils.generateServerId(packet.getServerId(), publicKey, secretKey))).toString(16);
            decrypt = NetworkEncryptionUtils.cipherFromKey(Cipher.DECRYPT_MODE, secretKey);
            encrypt = NetworkEncryptionUtils.cipherFromKey(Cipher.ENCRYPT_MODE, secretKey);
            loginPacket = new LoginKeyC2SPacket(secretKey, publicKey, packet.getNonce());
        } catch (NetworkEncryptionException e) {
            throw new IllegalStateException("Protocol error", e);
        }

        this.statusConsumer.accept(new TranslatableText("connect.authorizing"));
        NetworkUtils.EXECUTOR.submit(() -> {
            Text text = this.joinServerSession(serverId);
            if (text != null) {
                LOGGER.warn(text.getString());
            }

            this.statusConsumer.accept(new TranslatableText("connect.encrypting"));
            client.getConnection().send(loginPacket, future -> client.getConnection().setupEncryption(decrypt, encrypt));
        });
    }

    @Nullable
    private Text joinServerSession(String serverId) {
        try {
            client.getSessionService().joinServer(client.getSession().getProfile(), client.getSession().getAccessToken(), serverId);
            return null;
        } catch (AuthenticationUnavailableException ex1) {
            return new TranslatableText("disconnect.loginFailedInfo", new TranslatableText("disconnect.loginFailedInfo.serversUnavailable"));
        } catch (InvalidCredentialsException ex2) {
            return new TranslatableText("disconnect.loginFailedInfo", new TranslatableText("disconnect.loginFailedInfo.invalidSession"));
        } catch (InsufficientPrivilegesException ex3) {
            return new TranslatableText("disconnect.loginFailedInfo", new TranslatableText("disconnect.loginFailedInfo.insufficientPrivileges"));
        } catch (AuthenticationException ex4) {
            return new TranslatableText("disconnect.loginFailedInfo", ex4.getMessage());
        }
    }

    @Override
    public void onLoginSuccess(LoginSuccessS2CPacket packet) {
        this.statusConsumer.accept(new TranslatableText("connect.joining"));
        LOGGER.info("Login Success (UUID: {})", packet.getProfile().getId());
        this.profile = packet.getProfile();
        client.getConnection().setState(NetworkState.PLAY);
        client.getConnection().setPacketListener(new PlayNetworkHandler(client, client.getConnection(), profile));
    }

    @Override
    public void onDisconnect(LoginDisconnectS2CPacket packet) {
        client.getConnection().disconnect(packet.getReason());
    }

    @Override
    public void onCompression(LoginCompressionS2CPacket packet) {
        client.getConnection().setCompressionThreshold(packet.getCompressionThreshold(), false);
    }

    @Override
    public void onQueryRequest(LoginQueryRequestS2CPacket packet) {
        this.statusConsumer.accept(new TranslatableText("connect.negotiating"));
        client.getConnection().send(new LoginQueryResponseC2SPacket(packet.getQueryId(), null));
    }

    @Override
    public void onDisconnected(Text reason) {
        LOGGER.info("Disconnected (Reason: {})", reason.asString());
    }

    @Override
    public ClientConnection getConnection() {
        return client.getConnection();
    }
}

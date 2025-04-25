package com.uci.adapter.sunbird.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.provider.factory.AbstractProvider;
import com.uci.adapter.provider.factory.IProvider;
import com.uci.adapter.sunbird.web.inbound.SunbirdWebMessage;
import com.uci.adapter.sunbird.web.outbound.OutboundMessage;
import com.uci.adapter.sunbird.web.outbound.SunbirdMessage;
import com.uci.adapter.sunbird.web.outbound.SunbirdWebResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Function;

@Slf4j
@Getter
@Setter
@Builder
public class SunbirdWebPortalAdapter extends AbstractProvider implements IProvider {

    @Autowired
    @Qualifier("rest")
    private RestTemplate restTemplate;

    @Override
    public Mono<XMessage> convertMessageToXMsg(Object message) throws JAXBException, JsonProcessingException {
        SunbirdWebMessage webMessage = (SunbirdWebMessage) message;

        XMessagePayload xmsgPayload = XMessagePayload.builder()
                .text(webMessage.getText())
                .build();

        XMessage xMessage = XMessage.builder()
                .from(SenderReceiverInfo.builder().userID(webMessage.getFrom()).build())
                .to(SenderReceiverInfo.builder().userID("admin").build())
                .messageId(MessageId.builder()
                        .channelMessageId(webMessage.getMessageId())
                        .replyId(webMessage.getTo())
                        .build())
                .messageState(XMessage.MessageState.REPLIED)
                .messageType(XMessage.MessageType.TEXT)
                .channelURI("web")
                .providerURI("sunbird")
                .timestamp(Timestamp.valueOf(LocalDateTime.now()).getTime())
                .payload(xmsgPayload)
                .build();

        log.info("Converted inbound message to XMessage: {}", xMessage);
        return Mono.just(xMessage);
    }

    @Override
    public Mono<XMessage> processOutBoundMessageF(XMessage xMsg) throws Exception {
        log.info("Sending message to transport socket: {}", xMsg.toXML());

        OutboundMessage outboundMessage = getOutboundMessage(xMsg);
        String url = "http://transport-socket.ngrok.samagra.io/botMsg/adapterOutbound";

        return SunbirdWebService.getInstance()
                .sendOutboundMessage(url, outboundMessage)
                .map(response -> {
                    if (response != null) {
                        xMsg.setMessageId(MessageId.builder().channelMessageId(response.getId()).build());
                        xMsg.setMessageState(XMessage.MessageState.SENT);
                    }
                    return xMsg;
                });
    }

    @Override
    public void processOutBoundMessage(XMessage nextMsg) throws Exception {
        log.info("Processing outbound message: {}", nextMsg.toXML());
        callOutBoundAPI(nextMsg);
    }

    public XMessage callOutBoundAPI(XMessage xMsg) throws Exception {
        OutboundMessage outboundMessage = getOutboundMessage(xMsg);
        String url = "http://transport-socket.ngrok.samagra.io/adapterOutbound";

        SunbirdWebService webService = new SunbirdWebService();
        SunbirdWebResponse response = webService.sendText(url, outboundMessage);

        if (response != null) {
            xMsg.setMessageId(MessageId.builder().channelMessageId(response.getId()).build());
        }
        xMsg.setMessageState(XMessage.MessageState.SENT);
        return xMsg;
    }

    private OutboundMessage getOutboundMessage(XMessage xMsg) throws JAXBException {
        SunbirdMessage sunbirdMessage = SunbirdMessage.builder()
                .title(getCleanTextMessage(xMsg))
                .choices(getButtonChoices(xMsg))
                .build();

        return OutboundMessage.builder()
                .message(sunbirdMessage)
                .to(xMsg.getMessageId().getReplyId())
                .messageId(xMsg.getMessageId().getChannelMessageId())
                .build();
    }

    /**
     * Clean special characters and formatting from text message.
     */
    private String getCleanTextMessage(XMessage xMsg) {
        XMessagePayload payload = xMsg.getPayload();
        String cleanedText = payload.getText().replace("__", "").replace("\n\n", "");
        payload.setText(cleanedText);
        return cleanedText;
    }

    /**
     * Parse button choices and auto-generate keys from their text.
     */
    private ArrayList<ButtonChoice> getButtonChoices(XMessage xMsg) {
        ArrayList<ButtonChoice> choices = xMsg.getPayload().getButtonChoices();
        if (choices != null) {
            choices.forEach(choice -> {
                String[] words = choice.getText().split(" ");
                if (words.length > 0 && !words[0].isEmpty()) {
                    String key = words[0];
                    choice.setKey(key);
                    choice.setText(choice.getText().replaceFirst(key, "").trim());
                }
            });
        }
        return choices;
    }

    /**
     * Render choices as plain string for fallback or logging.
     */
    private String renderMessageChoices(ArrayList<ButtonChoice> buttonChoices) {
        if (buttonChoices != null && !buttonChoices.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (ButtonChoice choice : buttonChoices) {
                builder.append(choice.getText()).append("\n");
            }
            return builder.substring(0, builder.length() - 1); // Remove last newline
        }
        return "";
    }
}

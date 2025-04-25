package com.uci.adapter.sunbird.web.outbound;

import lombok.*;

/**
 * Represents the structure of an outbound message sent to the Sunbird Web channel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundMessage {
    private SunbirdMessage message;
    private String to;
    private String messageId;
}

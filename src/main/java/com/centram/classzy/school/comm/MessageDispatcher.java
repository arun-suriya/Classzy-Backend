package com.centram.classzy.school.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Records scheduled messages once their time arrives. It does not deliver them anywhere: no email or SMS provider is connected. */
@Component
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);
    private final MsgService service;
    private final TransactionTemplate tx;

    public MessageDispatcher(MsgService service, TransactionTemplate tx) { this.service = service; this.tx = tx; }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void run() {
        try { Integer n = tx.execute((s) -> service.dispatchDue()); if (n != null && n > 0) log.info("Recorded {} scheduled message(s)", n); }
        catch (RuntimeException e) { log.warn("Scheduled message check failed: {}", e.getMessage()); }
    }
}

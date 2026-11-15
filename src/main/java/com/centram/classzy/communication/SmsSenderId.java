package com.centram.classzy.communication;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sms_sender_ids")
public class SmsSenderId extends TenantEntity {
    public String senderId;
    public String senderType = "ALPHANUMERIC";
    public String status = "PENDING";
    public boolean defaultSender;
}

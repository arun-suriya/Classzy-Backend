package com.centram.classzy.institution;

import java.io.Serializable;
import java.util.Objects;

import com.centram.classzy.institution.InstitutionEnums.ImageKind;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "institution_images")
public class InstitutionImage {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "institution_id")
        private Long institutionId;
        @Enumerated(EnumType.STRING)
        @Column(name = "kind")
        private ImageKind kind;

        public Key() {
        }

        public Key(Long institutionId, ImageKind kind) {
            this.institutionId = institutionId;
            this.kind = kind;
        }

        public Long getInstitutionId() { return institutionId; }
        public ImageKind getKind() { return kind; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(institutionId, k.institutionId) && kind == k.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(institutionId, kind);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    protected InstitutionImage() {
    }

    public InstitutionImage(Long institutionId, ImageKind kind, String contentType, byte[] data) {
        this.id = new Key(institutionId, kind);
        this.contentType = contentType;
        this.data = data;
    }

    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }
}

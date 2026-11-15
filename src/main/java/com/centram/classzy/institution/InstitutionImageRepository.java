package com.centram.classzy.institution;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.institution.InstitutionEnums.ImageKind;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstitutionImageRepository extends JpaRepository<InstitutionImage, InstitutionImage.Key> {

    /** Which institutions have an image of this kind, without loading the bytes. */
    @Query("select i.id.institutionId from InstitutionImage i where i.id.kind = :kind")
    List<Long> institutionIdsWith(ImageKind kind);

    @Query("select count(i) > 0 from InstitutionImage i where i.id.institutionId = :institutionId and i.id.kind = :kind")
    boolean existsFor(Long institutionId, ImageKind kind);

    default Optional<InstitutionImage> find(Long institutionId, ImageKind kind) {
        return findById(new InstitutionImage.Key(institutionId, kind));
    }
}

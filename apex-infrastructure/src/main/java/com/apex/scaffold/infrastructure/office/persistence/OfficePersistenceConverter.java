package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficeIdentityBinding;
import com.apex.scaffold.domain.office.OfficePlatformApp;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficePersistenceConverter {
    OfficePlatformApp toApp(OfficeAppPO po);

    OfficeAppPO toPo(OfficePlatformApp app);

    OfficeIdentityBinding toBinding(OfficeBindingPO po);
}

package org.openstreetmap.atlas.geography.atlas.change;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.geography.atlas.builder.RelationBean;
import org.openstreetmap.atlas.geography.atlas.builder.RelationBean.RelationBeanItem;
import org.openstreetmap.atlas.geography.atlas.items.AtlasEntity;
import org.openstreetmap.atlas.geography.atlas.items.Relation;
import org.openstreetmap.atlas.geography.atlas.items.RelationMember;
import org.openstreetmap.atlas.geography.atlas.items.RelationMemberList;

/**
 * {@link Relation} that references a {@link ChangeAtlas}. That {@link Relation} makes sure that all
 * the member entitiess are "Change" types, and that all the parent {@link Relation}s are
 * {@link ChangeRelation}s.
 * <p>
 * NOSONAR here to avoid "Subclasses that add fields should override "equals" (squid:S2160)". Here
 * the equals from the parent works.
 *
 * @author matthieun
 */
public class ChangeRelation extends Relation // NOSONAR
{
    private static final long serialVersionUID = 4353679260691518275L;

    private final Relation source;
    private final Relation override;

    // Computing ChangeRelation members is very expensive, so we cache it here.
    private transient RelationMemberList membersCache;
    private transient Object membersCacheLock = new Object();

    // Computing Parent Relations is very expensive, so we cache it here.
    private transient Set<Relation> relationsCache;
    private transient Object relationsCacheLock = new Object();

    protected ChangeRelation(final ChangeAtlas atlas, final Relation source,
            final Relation override)
    {
        super(atlas);
        this.source = source;
        this.override = override;
    }

    @Override
    public RelationMemberList allKnownOsmMembers()
    {
        return membersFor(
                attribute(Relation::allKnownOsmMembers, "all known osm members").asBean());
    }

    @Override
    public List<Relation> allRelationsWithSameOsmIdentifier()
    {
        return attribute(Relation::allRelationsWithSameOsmIdentifier,
                "all relations with same osm identifier").stream()
                        .map(relation -> getChangeAtlas().relation(relation.getIdentifier()))
                        .collect(Collectors.toList());
    }

    @Override
    public long getIdentifier()
    {
        return attribute(Relation::getIdentifier, "identifier");
    }

    @Override
    public Map<String, String> getTags()
    {
        return attribute(Relation::getTags, "tags");
    }

    @Override
    public RelationMemberList members()
    {
        final Supplier<RelationMemberList> creator = () ->
        {
            final List<RelationMemberList> availableMemberLists = allAvailableAttributes(
                    Relation::members, "members");
            final RelationBean mergedMembersBean = availableMemberLists.stream()
                    .map(RelationMemberList::asBean)
                    .reduce(new RelationBean(), RelationBean::merge);
            final RelationBean filteredAndMergedMembersBean = new RelationBean();
            mergedMembersBean.forEach(relationBeanItem ->
            {
                if (getChangeAtlas().entity(relationBeanItem.getIdentifier(),
                        relationBeanItem.getType()) != null)
                {
                    filteredAndMergedMembersBean.addItem(relationBeanItem);
                }
            });
            return membersFor(filteredAndMergedMembersBean);
        };

        return ChangeEntity.getOrCreateCache(this.membersCache, cache -> this.membersCache = cache,
                this.membersCacheLock, creator);
    }

    @Override
    public Long osmRelationIdentifier()
    {
        return attribute(Relation::osmRelationIdentifier, "osm relation identifier");
    }

    @Override
    public Set<Relation> relations()
    {
        final Supplier<Set<Relation>> creator = () -> ChangeEntity
                .filterRelations(attribute(AtlasEntity::relations, "relations"), getChangeAtlas());
        return ChangeEntity.getOrCreateCache(this.relationsCache,
                cache -> this.relationsCache = cache, this.relationsCacheLock, creator);
    }

    private <T extends Object> List<T> allAvailableAttributes(
            final Function<Relation, T> memberExtractor, final String name)
    {
        return ChangeEntity.getAttributeAndOptionallyBackup(this.source, this.override,
                memberExtractor, name);
    }

    private <T extends Object> T attribute(final Function<Relation, T> memberExtractor,
            final String name)
    {
        return ChangeEntity.getAttributeOrBackup(this.source, this.override, memberExtractor, name);
    }

    private ChangeAtlas getChangeAtlas()
    {
        return (ChangeAtlas) getAtlas();
    }

    private RelationMemberList membersFor(final RelationBean bean)
    {
        if (bean == null)
        {
            return null;
        }
        final List<RelationMember> memberList = new ArrayList<>();
        for (final RelationBeanItem item : bean)
        {
            final AtlasEntity memberChangeEntity = getChangeAtlas().entity(item.getIdentifier(),
                    item.getType());
            if (memberChangeEntity != null)
            {
                memberList.add(
                        new RelationMember(item.getRole(), memberChangeEntity, getIdentifier()));
            }
        }
        return new RelationMemberList(memberList);
    }
}

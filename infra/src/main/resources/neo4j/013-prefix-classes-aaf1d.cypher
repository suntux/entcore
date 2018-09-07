match (c:Class)-[:BELONGS]->(s:Structure) where c.externalId =~ '[0-9]+' and s.externalId =~ '[A-Z]+\\-[0-9]+' set  c.externalId = split(s.externalId, '-')[0] + '-' + c.externalId;
match (c:Class)-[:BELONGS]->(s:Structure) where c.externalId =~ '[0-9]+' and s.externalId =~ '[A-Z]+\\-[A-Z]+\\-[0-9]+' set  c.externalId = split(s.externalId, '-')[0] + '-' + split(s.externalId, '-')[1] + '-' + c.externalId;
match (c:Class)-[:BELONGS]->(s:Structure) where c.externalId =~ '[0-9]+' and head(s.joinKey) =~ '[A-Z]+\\-[0-9]+' set  c.externalId = split(head(s.joinKey), '-')[0] + '-' + c.externalId;
match (c:Class)-[:BELONGS]->(s:Structure) where c.externalId =~ '[0-9]+' and head(s.joinKey) =~ '[A-Z]+\\-[A-Z]+\\-[0-9]+' set  c.externalId = split(head(s.joinKey), '-')[0] + '-' + split(head(s.joinKey), '-')[1] + '-' + c.externalId;


package ma.fsac.pfeecom.cart.config;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Générateur d'identifiants numériques pour MongoDB via un compteur atomique.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
public class SequenceGenerator {

    private final MongoTemplate mongoTemplate;

    public SequenceGenerator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Long nextSeq(String seqName) {
        Document counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(seqName)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class,
                "sequences");
        return counter == null ? 1L : ((Number) counter.get("seq")).longValue();
    }
}
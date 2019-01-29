CREATE OR REPLACE FUNCTION conversation.forward_receivers() RETURNS TRIGGER AS 
$$
DECLARE
    var_parent_id varchar(36);
    parent_state varchar(10);
BEGIN
	IF (TG_OP = 'INSERT') THEN
        SELECT messages.state,messages.parent_id into parent_state,var_parent_id from conversation.messages where id = (SELECT messages.parent_id FROM conversation.messages WHERE messages.id=NEW.message_id);
        IF ( parent_state = 'FORWARD' ) THEN
        	INSERT INTO conversation.usermessages(user_id, message_id, folder_id, trashed, unread, total_quota) VALUES (NEW.user_id,var_parent_id,NEW.folder_id,NEW.trashed,NEW.unread,NEW.total_quota);
        	UPDATE conversation.messages SET state = 'SENT' where id = var_parent_id; 
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ 
LANGUAGE plpgsql;

CREATE TRIGGER forward_receivers
AFTER INSERT ON conversation.usermessages
    FOR EACH ROW EXECUTE PROCEDURE conversation.forward_receivers();
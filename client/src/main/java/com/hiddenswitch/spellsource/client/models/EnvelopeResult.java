/*
 * Hidden Switch Spellsource API
 * The Spellsource API for matchmaking, user accounts, collections management and more.  To get started, create a user account and make sure to include the entirety of the returned login token as the X-Auth-Token header. You can reuse this token, or login for a new one.  ClientToServerMessage and ServerToClientMessage are used for the realtime game state and actions two-way websocket interface for actually playing a game. Envelope is used for the realtime API services. 
 *
 * OpenAPI spec version: 2.1.0
 * Contact: ben@hiddenswitch.com
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package com.hiddenswitch.spellsource.client.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hiddenswitch.spellsource.client.models.DefaultMethodResponse;
import com.hiddenswitch.spellsource.client.models.EnvelopeResultSendMessage;
import com.hiddenswitch.spellsource.client.models.MatchmakingQueuePutResponse;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * When populated on the server and sent to the client, indicates that a method call had a specific result. 
 */
@ApiModel(description = "When populated on the server and sent to the client, indicates that a method call had a specific result. ")
@JsonInclude(JsonInclude.Include.NON_DEFAULT)

public class EnvelopeResult implements Serializable {
  private static final long serialVersionUID = 1L;

  @JsonProperty("methodId")
  private String methodId = null;

  @JsonProperty("sendMessage")
  private EnvelopeResultSendMessage sendMessage = null;

  @JsonProperty("enqueue")
  private MatchmakingQueuePutResponse enqueue = null;

  @JsonProperty("dequeue")
  private DefaultMethodResponse dequeue = null;

  public EnvelopeResult methodId(String methodId) {
    this.methodId = methodId;
    return this;
  }

   /**
   * The ID of the method this is a result for. 
   * @return methodId
  **/
  @ApiModelProperty(value = "The ID of the method this is a result for. ")
  public String getMethodId() {
    return methodId;
  }

  public void setMethodId(String methodId) {
    this.methodId = methodId;
  }

  public EnvelopeResult sendMessage(EnvelopeResultSendMessage sendMessage) {
    this.sendMessage = sendMessage;
    return this;
  }

   /**
   * Get sendMessage
   * @return sendMessage
  **/
  @ApiModelProperty(value = "")
  public EnvelopeResultSendMessage getSendMessage() {
    return sendMessage;
  }

  public void setSendMessage(EnvelopeResultSendMessage sendMessage) {
    this.sendMessage = sendMessage;
  }

  public EnvelopeResult enqueue(MatchmakingQueuePutResponse enqueue) {
    this.enqueue = enqueue;
    return this;
  }

   /**
   * Get enqueue
   * @return enqueue
  **/
  @ApiModelProperty(value = "")
  public MatchmakingQueuePutResponse getEnqueue() {
    return enqueue;
  }

  public void setEnqueue(MatchmakingQueuePutResponse enqueue) {
    this.enqueue = enqueue;
  }

  public EnvelopeResult dequeue(DefaultMethodResponse dequeue) {
    this.dequeue = dequeue;
    return this;
  }

   /**
   * Get dequeue
   * @return dequeue
  **/
  @ApiModelProperty(value = "")
  public DefaultMethodResponse getDequeue() {
    return dequeue;
  }

  public void setDequeue(DefaultMethodResponse dequeue) {
    this.dequeue = dequeue;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EnvelopeResult envelopeResult = (EnvelopeResult) o;
    return Objects.equals(this.methodId, envelopeResult.methodId) &&
        Objects.equals(this.sendMessage, envelopeResult.sendMessage) &&
        Objects.equals(this.enqueue, envelopeResult.enqueue) &&
        Objects.equals(this.dequeue, envelopeResult.dequeue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodId, sendMessage, enqueue, dequeue);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EnvelopeResult {\n");
    
    sb.append("    methodId: ").append(toIndentedString(methodId)).append("\n");
    sb.append("    sendMessage: ").append(toIndentedString(sendMessage)).append("\n");
    sb.append("    enqueue: ").append(toIndentedString(enqueue)).append("\n");
    sb.append("    dequeue: ").append(toIndentedString(dequeue)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

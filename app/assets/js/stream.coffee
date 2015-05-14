$ ->
  ws = new WebSocket $("#twitstream").data("ws-url")

  ws.onclose = (error) ->
    console.log("[" + error.code + "] WebSocket closed")
    window.location = "/relogin"

  ws.onmessage = (event) ->
#    console.log("Received event on WebSocket")
#    console.log(event.data)
    message = JSON.parse event.data

    if (message.type == 'status')
      console.log("Status received: " + message.data)
      if (message.data == 'ACTOR_READY')
        $("#tweets").show("slow")
      else
        console.log("Undefine status '" + message.data + "'")

    else if (message.type == 'tweet')
      tweet = message.data
      tweetsShown = $("#tweets").data("tweets-shown")
      console.log("tweetsShown = " + tweetsShown)
      if (tweetsShown < 0)
        console.log("showing tweet")
        showTweet(tweet)
      else
        console.log("queueing tweet")
        queueTweet(tweet)

    else if (message.type == 'error')
      console.log("[error]: " + message.data + " / " + message.error)
      error = message.error
      showError(error)


  tweetQ = []

  queueTweet = (tweet) ->
    tweetQ.push(tweet)
    console.log("queued " + tweetQ.length + " tweets")
    newtweets = $(".new-tweets-bar").data("tweets-count")
    newtweets++
    $("#new-tweets-count").text(newtweets)
    $(".new-tweets-bar").data("tweets-count", newtweets)
    $(".new-tweets-container").show("slow")

  $(".new-tweets-container").click ->
    console.log("Showing Tweets")
    $(".new-tweets-container").hide()
    showTweet(newtweet) for newtweet in tweetQ
    tweetQ = []
    $(".new-tweets-bar").data("tweets-count", 0)


  $('#search').click ->
    $('#search').focus()

  String::strip = -> if String::trim? then @trim() else @replace /^\s+|\s+$/g, ""

  $("#searchBtn").click (event) ->
    event.preventDefault()
    searchQuery = $("#search").val()
    if (searchQuery.strip() != "")
      jsonRequest = JSON.stringify({term: searchQuery, reqType: 'search'})
      serverRequest(jsonRequest)
      $('#search-query').text(searchQuery)
      $('#twitstream').show()

  $("#addBtn").click (event) ->
    event.preventDefault()
    jsonRequest = JSON.stringify({term: $("#search").val(), reqType: 'add'})
    serverRequest(jsonRequest)
    $("#search").val("")

  $("#stopBtn").click (event) ->
    event.preventDefault()
    serverRequest(JSON.stringify(reqType: "action", action: "StopWatching"))
    $("#search").val("")

  $(".twitstream").on "click", ".timeline-link", (event) ->
    clickedLink = $(event.currentTarget)
    expandedUrl = clickedLink.data("expanded-url")
    console.log(clickedLink + " -- url -- " + expandedUrl)
    if (expandedUrl.length > 0)
      showArticle(expandedUrl)
    else
      console.log("No expanded URL")

  $(".alert-dismissable").click ->
    $(event.currentTarget).hide('slow')

  serverRequest = (request) ->
    ws.send(request)
    console.log("Sent -- " + request)

  showTweet = (tweet) ->
    $('<li class="tweet">' +
      '<div class="tweetcontent media">' +
      '<div class="media-left">' +
      '<img src="' + tweet.img + '" class="avatar">' +
      '</div>' +
      '<div class="media-body">' +
      '<div class="tweet-header">' +
      '<strong class="username">' + tweet.user + '</strong><span>&nbsp;</span><span class="handle">@' + tweet.screenName + '</span>' +
      '</div>' +
      '<p class="tweettext">' + tweet.text + '</p>' +
      '<div class="metadata">' + tweet.createdAt + '</div>' +
      '</div>' +
      '</div>' +
      '</li>')
    .hide().prependTo('#tweets').slideDown()
    tweetsShown = $("#tweets").data("tweets-shown", ) + 1
    $("#tweets").data("tweets-shown", tweetsShown)

  showError = (error) ->
    $('#error-text').text(error)
    $('#error').show('slow')

  showArticle = (url) ->
    $.ajax
      url: "getpreview?url=" + encodeURI(url)
      dataType: "json"
      error: (jqXHR, textStatus, errorThrown) ->
        showError("Couldn't fetch article: #{errorThrown}")
      success: (data, textStatus, jqXHR) ->
        if (data.status == true)
          $("#article-preview").html('<h2 class="article-preview-title">' + data.title + '</h2>' +
          '<p class="article-preview-body">' + data.content + '</p>')
        else
          showError(data.title + ": " + data.content)
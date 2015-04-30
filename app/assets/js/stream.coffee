$ ->
  ws = new WebSocket $("#twitstream").data("ws-url")

  ws.onmessage = (event) ->
    console.log("Received event on WebSocket")
    console.log(event.data)
    message = JSON.parse event.data

    if (message.type == 'status')
      console.log("Status received: " + message.data)
      if (message.data == 'ACTOR_READY')
        $("#tweets").show("slow")
      else
        console.log("Undefine status '" + message.data + "'")
    else if (message.type == 'tweet')
      tweet = message.data
      showTweet(tweet)

  $('#search').click (event) ->
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
    serverRequest("StopWatching")
    $("#search").val("")

  serverRequest = (request) ->
    ws.send(request)
    console.log("Sent -- " + request)

  showTweet = (tweet) ->
    $('<li class="tweet">' +
        '<div class="tweetcontent">' +
          '<div class="tweet-header">' +
            '<img src="' + tweet.img + '" class="avatar">' +
            '<strong class="username">' + tweet.user + '</strong>' +
            '<span class="handle">@' + tweet.screenName + '</span>' +
          '</div>' +
          '<p class="tweettext">' + tweet.text + '</p>' +
            '<div class="metadata">' + tweet.createdAt + '&nbsp;|&nbsp;' + tweet.source + '</div>' +
        '</div>' +
      '</li>')
      .hide().prependTo('#tweets').slideDown()
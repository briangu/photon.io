var photonApp = function()
{
    var start = 0;
    var count = 10;

    function createUploader()
    {
        var pub_uploader = new qq.FileUploader({
                                                   element: document.getElementById('file-uploader-demo1'),
                                                   action: '/u/',
                                                   debug: false,
                                                   params: params,
                                                   allowedExtensions: ["jpg", "jpeg", "gif", "png", "tiff", "bmp"],
                                                   onComplete: function(id, filename, responseJSON)
                                                   {
                                                       if (!responseJSON.success) return;
                                                       $.post('/photos/add', {
                                                                  id: params.id,
                                                                  thumbnail: responseJSON.thumbnail,
                                                                  url: responseJSON.url,
                                                                  photoId: "urn:photo:" + responseJSON.key
                                                              }, function(response)
                                                              {
                                                                  //_getFeed();
                                                              });
                                                   }
                                               });
    }

    function fixBrokenImages()
    {
        $("img").each(function()
                      {
                          this.onerror = function ()
                          {
                              this.src = "/images/no-image.png";
                              console.log("An error occurred loading the image.");
                          };
                      });
    }

    function showSpinner()
    {
        var spinner = new Spinner().spin();
        spinner.el.className = "spinner";
        $('#stream-updates').html(spinner.el);
    }

    function _getFeed()
    {
        loading_next_page = true;
        showSpinner();
        $.getJSON('/photos/feed?id=' + params.id + '&start=' + start + "&count=" + count, function(data)
        {
            $.each(data.elements, function(key, activity)
            {
                image = activity.object["com.linkedin.ucp.ObjectSummary"].image;
//           $('#posts').append("<li class='post photo'><img class='carousel_img' src=\""+image+"\"></li>");
                $('#posts').append(renderActivity(activity, '#template-post'));
                activityIdMap[activity.object.id] = activity.id;
            });

            fixBrokenImages();
            showTimeAgoDates();
            loading_next_page = false;
        });
    }

    var _imgInfo;

    function imageData(imgInfo)
    {
        _imgInfo = imgInfo
        var url = imgInfo[0];
        var photoId = url.substring(url.lastIndexOf("/") + 1);
        var threadId = activityIdMap["urn:photo:" + photoId];

        $.getJSON('/photos/photocomments/?threadId=' + threadId, function(data)
                  {
                      $('#comment-stream').remove();
                      var html = "<div id='comment-stream' class='container' style='margin-left: 50px'>";
                      var i = 0;
                      $.each(data.elements, function(key, activity)
                      {
                          if (i++ >= 3) return;
                          html += renderActivity(activity, "#template-photo-comment");
                      })

                      html += Mustache.to_html($("#template-photo-comment-box").html())

                      html += "<br/><a href='" + url + "' target='_blank'>" + url + "</a><br/>";
                      html += "</div>";

                      $('#lightbox-container-image-data-box').prepend(html);

                      attachCommentHandlers(imgInfo, threadId);
                  });
    }

    function attachCommentHandlers(imgInfo, threadId)
    {
        $(".comment-button").click(function ()
                                   {
                                       commentBox = $(".comment-box")
                                       commentTextArea = commentBox.find(".comment-textarea")
                                       commentText = commentTextArea.val();
                                       if (commentText.length > 0)
                                       {
                                           $.ajax({
                                                      type: 'POST',
                                                      url: '/photos/comments/?id=' + params.id + "&threadId=" + threadId,
                                                      data: "message=" + escape(commentText),
                                                      success: function(data)
                                                      {
                                                          setTimeout(function()
                                                                     {
                                                                         imageData(imgInfo)
                                                                     }, 1000);
                                                          commentTextArea.val("");
                                                          commentBox.hide(500);
                                                      },
                                                      error: function(data, textStatus, errorThrown)
                                                      {
                                                          alert("Error posting comment: " + errorThrown);
                                                      }
                                                  });
                                       }
                                       else
                                       {
                                           commentBox.effect('shake', { times: 2 }, 200);
                                       }
                                   });

        $(".cancel-comment").click(function ()
                                   {
                                       $(".comment-box").hide(500);
                                   });
    }

    function showTimeAgoDates()
    {
        // Show timeago
        $(".easydate").each(function()
                            {
                                var previousDate = parseInt($(this).attr("rel"), 10);
                                var date = new Date(previousDate);
                                $(this).html($.easydate.format_date(date));
                            });
    }

    /**
     * Just takes an activity and template id, and adds the resulting html to the stream
     */
    function renderActivity(activity, templateId)
    {
        var template = $(templateId).html();
        return Mustache.to_html(template, activity);
    }

    var loading_next_page = false;

    function auto_paginator(pe)
    {
        if (loading_next_page) return;

        var posts = $('#posts > li');
        var is_scrollable = $(document).height() - $(window).height() <= $(window).scrollTop() + 50;

        if (is_scrollable)
        {
            loading_next_page = true;
            $('auto_pagination_loader_loading').show();
            $('auto_pagination_loader_failure').hide();

            start = posts.length - 1;
            count = 10;
            _getFeed();
        }
        else
        {
            if ($('auto_pagination_loader'))
            {
                $('auto_pagination_loader').hide()
            }
        }
    }

   createUploader();
    _getFeed();
    setInterval(auto_paginator, 200);
};

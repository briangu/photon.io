var photonApp = function()
{
    var start = 0;
    var count = 10;
    var loading_next_page = false;
    var keywords = "";
    var disableAutoPaginate = false;

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
                                                       photoList.push({
                                                                          thumbnail: responseJSON.thumbnail,
                                                                          url: responseJSON.url,
                                                                          filename: filename,
                                                                          photoId: responseJSON.key
                                                                      });
                                                   /*
                                                       $.post('/photos/add', {
                                                                  id: params.id,
                                                                  thumbnail: responseJSON.thumbnail,
                                                                  url: responseJSON.url,
                                                                  filename: filename,
                                                                  photoId: responseJSON.key
                                                              }, function(response)
                                                              {
                                                                fetchActivityList[response.meta.Id] = 0;
                                                              });
                                                   */
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
 /*
        var spinner = new Spinner().spin();
        spinner.el.className = "spinner";
        $('#loading_icon').html(spinner.el);
*/
        $('#loading_icon_spinner').show();
    }

    function hideSpinner()
    {
//        $('#loading_icon').html('');
        $('#loading_icon_spinner').hide();
    }

    function _getPublicFeed()
    {
        loading_next_page = true;
        showSpinner();

        $.getJSON('/feeds/public?id=' + params.id + '&start=' + start + "&count=" + count + "&keywords=" + keywords, function(data)
        {
            disableAutoPaginate = (data.elements.length < count);

            $.each(data.elements, function(key, activity)
            {
                renderResult = renderActivity(activity);
                $('#posts').append(renderResult['html']);
                if (renderResult['applyCarousel']) applyCarousel(activity);
                activityIdMap[activity.object.id] = activity.id;
            });

            fixBrokenImages();
            showTimeAgoDates();
            attachCommentListeners();

            loading_next_page = false;
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

    function renderActivity(activity)
    {
        activity.idEscaped = activity.id.replace(':', '_')

        useCarousel = false;

        if (activity.verb.type == "photon:create_album")
        {
            images = new Array();
            properties = activity['object']['com.linkedin.ucp.ObjectSummary']['properties'];
            $.each(properties, function(idx, val){
                content = JSON.parse(val.content);
                images.push({'thumbnail': content['thumbnail']});
            });
            activity['object']['com.linkedin.ucp.ObjectSummary'].images = images;

            if (images.length < 3)
            {
                corePost = applyTemplate(activity, '#template-create_album-vertical');
            }
            else
            {
                corePost = applyTemplate(activity, '#template-create_album-carousel');
                useCarousel = true;
            }
        }
        else
        {
            corePost = applyTemplate(activity, '#template-default');
        }

        activity.corepost = corePost;

        result = {};
        result['html'] = applyTemplate(activity, "#template-post");
        result['applyCarousel'] = useCarousel;

        return result;
    }

    function applyTemplate(activity, templateId) {
        var template = $(templateId).html();
        return Mustache.to_html(template, activity);
    }

    function auto_paginator()
    {
        if (loading_next_page || disableAutoPaginate) return;

        var posts = $('#posts > li');
        var is_scrollable = $(document).height() - $(window).height() <= $(window).scrollTop() + 50;

        if (is_scrollable)
        {
            if ($('#auto_pagination_loader'))
            {
                $('#auto_pagination_loader').show()
            }
            loading_next_page = true;
            $('auto_pagination_loader_loading').show();
            $('auto_pagination_loader_failure').hide();

            start = posts.length - 1;
            count = 10;
            _getPublicFeed();
        }
        else
        {
            if ($('#auto_pagination_loader'))
            {
                $('#auto_pagination_loader').hide()
            }
        }
    }

    function activity_fetcher()
    {
        var cloneActivityList = jQuery.extend({}, fetchActivityList);

        if (Object.keys(cloneActivityList).length > 0)
        {
            showSpinner();
        }
        else
        {
            hideSpinner();
        }

        $.each(cloneActivityList, function(activityId, attempts)
        {
            $.getJSON('/posts/' + activityId, function(data)
            {
                if (data.elements.length == 0)
                {
                    attempts++;
                    if (attempts > 40)
                    {
                        delete fetchActivityList[activityId];
                    }
                    fetchActivityList[activityId] = attempts;
                    return;
                }

                delete fetchActivityList[activityId];

                $.each(data.elements, function(key, activity)
                {
                    renderResult = renderActivity(activity);
                    $('#posts .is_mine').after(renderResult['html']);
                    if (renderResult['applyCarousel']) applyCarousel(activity);
                    activityIdMap[activity.object.id] = activity.id;
                });

                fixBrokenImages();
                showTimeAgoDates();
            });
        });
    }

    function applyCarousel(activity)
    {
        id = activity.id.replace(':','_');

        $(".widget_"+id+" .jCarouselLite").jCarouselLite({
            btnNext: ".widget_"+id+" .next",
            btnPrev: ".widget_"+id+" .prev",
            speed: 800
        });

        $(".widget_"+id+" img").click(function() {
            console.log(id);
            $(this).parents('.carousel').children('.mid').children('img').attr("src", $(this).attr("src"));
        })
    }

    function attachCommentListeners() {
       $(".add-comment").click(function () {
         $(".comment-box").slideUp('fast'); // Hide all other comments
         $(this).parent().parent().find(".comment-box").slideDown('fast');
         $(this).parent().parent().find(".comment-box textarea").focus();
       });

       $(".comment-button").click(function () {
         commentBox = $(this).parents(".comment-box");
         commentTextArea = commentBox.find(".comment-textarea");
         commentText = commentTextArea.val();
         if (commentText.length > 0) {
           m = commentBox.attr('id').match("comment-box-activity_(.*)");
           activityUrn = "activity:"+m[1];
           $.ajax({
             type: 'POST',
             url: '/activities/' + activityUrn + '/comments',
             data: {
                 "message": commentText,
                 "id": params.id,
                 "name": params.name
             },
             success: function(data) {
               commentBox.hide('fast');
               var commentActivity = data;
               console.log(commentActivity);
               var renderedComment = applyTemplate(commentActivity, "#template-render-event-comment"); // Render the template corresponding to the comment event just created
               commentBox.parent().find(".comments").prepend(renderedComment);
               commentTextArea.val(""); // clear entered text
               showTimeAgoDates();
             },
             error: function(data, textStatus, errorThrown) {
               // alert("Error posting comment: " + errorThrown);
             }
           });
         } else {
           commentBox.effect('shake', { times: 2 }, 200);
         }
       });

       $(".cancel-comment").click(function () {
         $(this).parents(".comment-box").hide('fast');
       });

       $(".add-like").click(function () {
         link = $(this);
         m = link.attr('id').match("like-(.*)");
         objectId = m[1];
         isLiked = "Unlike" == link.html().trim();

         $.ajax({
           type: isLiked ? 'DELETE' : 'PUT',
           url: '/me/likes/member:' + params.id + "/" + objectId.replace('_', ':'),
           data: "{}",
           success: function(data) {
             link.text(isLiked ? "Like" : "Unlike");
           },
           error: function(data, textStatus, errorThrown) {
             // alert("Error liking activity: " + errorThrown);
           }
         });
       });
    }

    $('#text_input_form').submit(
      function(e) {
        msg = $('#text_caption').val();
        $.post('/text/add', {
                  id: params.id,
                  msg: msg
               }, function(response)
               {
                 fetchActivityList[response.meta.Id] = 0;
                 hideInput();
               });
        return false;
      }
    );

    $('#photo_input_form').submit(
      function(e) {
        msg = $('#photo_caption').val();

        $.post('/photos/add', {
                   id: params.id,
                   msg: msg,
                   photoList: JSON.stringify(photoList)
               }, function(response)
               {
                 fetchActivityList[response.meta.Id] = 0;
                 hideInput();
               });

        return false;
      }
    );

    $('#link_input').submit(
      function(e) {
        $.post('/text/add', {
                  id: params.id,
                  title: $('#link_title').val(),
                  description: $('#link_caption').val(),
                  link: $('#link_url').val(),
               }, function(response)
               {
                 fetchActivityList[response.meta.Id] = 0;
                 hideInput();
               });
        return false;
      }
    );

    $(".cancel-post").click(function () {
      $(this).parents(".post_input").hide('fast');
    });

    $('#search_form').submit(
      function(e) {
        keywords = $('#search_query').val();
        start = 0;
        $('.not_mine').remove();
        _getPublicFeed();
        return false;
      }
    );

    createUploader();
    _getPublicFeed();
    setInterval(auto_paginator, 200);
    setInterval(activity_fetcher, 250);
};


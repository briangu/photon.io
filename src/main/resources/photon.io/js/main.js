var snapclearApp = function (initdata) {
    'use strict';

    function updateTrends(trends) {
      $('.trends li').remove();
      $('.trends-title').html(trends.title);
      $.each(trends.results, function(i,tag){
        var displayTag
        if (tag.substr(0,1) != "#") {
          displayTag = "#" + tag
        } else {
          displayTag = tag
        }
        $('.trends').append('<li><a class="toptag" href="?tags='+tag+'">' + displayTag +'</a></li>')
      });
    }

    updateTrends(initdata.trends);

    function enableUpload(enable) {
      if (enable) {
        if ($('.download-panel').is(':hidden')) {
          clearFileList();
          $('.download-panel').show();
        }
      }
    }

    function haveTags() {
      return $('.box-editor').val().length > 0 && $('.box-editor').val() != window.locale.fileupload.boxeditor;
    }

    function clearFileList() {
      $('.upload-file-queue tr').remove();
    }

    function haveFilesQueued() {
      return $('.upload-file-queue tr').size() > 0;
    }

    function isUploadPending() {
      return haveFilesQueued();
    }

    function resetBoxEditor() {
      $('.box-editor').val(window.locale.fileupload.boxeditor)
    }

    function attachItemsShareActions() {
      $('.item[data-sharable="true"]').each(function(idx, item) { attachItemShareActions(item); });
    }

    function attachItemShareActions(item) {
      $(item).hover(
        function(e) {
          $(this).find('.item-actions').filter(function(index) { return !$(this).find("input[name='share[]']").is(':checked'); }).show();
        },
        function(e) {
          $(this).find('.item-actions').filter(function(index) { return !$(this).find("input[name='share[]']").is(':checked'); }).hide();
        }
      );

      $(item).find('.item-share').click(function(e) {
        resetShareModal();
        addToShareList($(this).closest('.item'));
        $('#modal-share').modal({})
      })
    }

    function unattachItemsShareActions() {
      $('.item').each(function (idx, item) {
        $(item).unbind('hover');
        $(item).find('.item-share').unbind('click');
      });
    }

    function attachItemsTagActions() {
      $('.item').each(function(idx, item) { attachItemTagActions(item); });
    }

    $('#modal-tag').on('shown', function () {
      $('#modal-tag .collect-tags')[0].focus();
    })

/*
    $('#modal-lightbox').on('shown', function () {
      $('html').css({overflow:'hidden'})
    })

    $('#modal-lightbox').on('hidden', function () {
      $('html').css({overflow:'auto'})
    })
*/

    function attachItemTagActions(item) {
      $(item).find('.item-collect').click(function(e) {
        resetTagModal();
        addToTagList($(this).closest('.item'));
        $('#modal-tag').modal({})
      })
    }

    function unattachItemsShareActions() {
      $('.item').each(function (idx, item) {
        $(item).unbind('hover');
        $(item).find('.item-share').unbind('click');
      });
    }

    function initUI() {
      resetBoxEditor();

      $('.box-editor').change(function() {
        enableUpload(isUploadPending());
      });

      $('.box-editor').live('keyup', function() {
        enableUpload(isUploadPending());
      });

      $('.box-editor').focus(function() {
        $('.box-editor').val("");
      });

      $('.box-editor').blur(function() {
        if ($('.box-editor').val().length == 0) {
          $('.box-editor').val(window.locale.fileupload.boxeditor);
        }
      });

      $('.upload-button').click(function() {
        if (!haveTags()) {
          alert("Before you upload your files, you should tag them.")
          return false;
        }
        toggleUploadCloseAll();
        $('.files').find('.start button').click();
        return true;
      })
    }

    var template = $('#template-mason-brick').html();

/*
    $('#fileupload').fileupload();
    $('#fileupload').fileupload('option', {
        url: '/u/',
        autoUpload: false,
        multipart: true,
        filesContainer: $('#fileupload .files'),
        fileInput: $('.nav .fileinput-button input'),
//        acceptFileTypes: /(\.|\/)(gif|jpe?g|png)$/i,
        process: [
            {
                action: 'load',
                fileTypes: /^image\/(gif|jpeg|png)$/,
                maxFileSize: 20000000 // 20MB
            },
            {
                action: 'save'
            }
        ]
    });

    $('#fileupload').bind('fileuploadsubmit', function (e, data) {
      data.formData = {'tags': $('#tags').val()};
    });

    $('#fileupload').bind('fileuploaddrop', function (e, data) {
      enableUpload(true);
    });

    $('#fileupload').bind('fileuploadadd', function (e, data) {
      enableUpload(true);
    });

    $('#fileupload').bind('fileuploadstop', function (e, data) {
      if ($('.upload-file-queue tr').size() == 1) {
        enableUpload(false);
      }
      toggleUploadCloseAll();
      toggleUploadPanelClosable();
    });

    $('#fileupload').bind('fileuploaddone', function (e, data) {
      var x = JSON.parse(data.jqXHR.responseText)[0]
      var newElements = processItem(template, x);
      var $gallery = $('#gallery')
//      $('.corner-stamp').append(h);
      var $newElems = $( newElements ).css({ opacity: 0 });
      $gallery.prepend($newElems);
      $gallery.imagesLoaded(function(){
        $gallery.masonry('reload')
        $newElems.animate({ opacity: 1 });
        $($newElems).each(function (idx, item) {
          attachItemShareActions(item);
          enableItemLightbox(item);
        })
        showTimeAgoDates();
      });
    });

    $('#fileupload')
      .bind('fileuploadadd', function (e, data) {console.log("fileuploadadd")})
      .bind('fileuploadsubmit', function (e, data) { console.log("fileuploadsubmit") })
      .bind('fileuploadsend', function (e, data) {console.log("fileuploadsend")})
      .bind('fileuploaddone', function (e, data) {console.log("fileuploaddone")})
      .bind('fileuploadfail', function (e, data) {console.log("fileuploadfail")})
      .bind('fileuploadalways', function (e, data) {console.log("fileuploadalways")})
      .bind('fileuploadprogress', function (e, data) {console.log("fileuploadprogress")})
      .bind('fileuploadprogressall', function (e, data) {console.log("fileuploadprogressall")})
      .bind('fileuploadstart', function (e) {console.log("fileuploadstart")})
      .bind('fileuploadstop', function (e) {console.log("fileuploadstop")})
      .bind('fileuploadchange', function (e, data) {console.log("fileuploadchange")})
      .bind('fileuploadpaste', function (e, data) {console.log("fileuploadpaste")})
      .bind('fileuploaddrop', function (e, data) {console.log("fileuploaddrop")})
      .bind('fileuploaddragover', function (e) {console.log("fileuploaddragover")});
*/


/*
DISABLED
    $('#fileupload').bind('fileuploadfail', function (e, data) {
      if ($('.upload-file-queue tr').size() == 1) {
        enableUpload(false);
      }
    });
*/

    initUI();

    function showTimeAgoDates() {
      $(".easydate").each(function() {
//        $(this).html($.easydate.format_date(new Date(parseInt($(this).attr("data-filedate"), 10))));
        $(this).html(
          $.easydate.format_date(
            new Date($(this).attr("data-filedate"))),
            {
              units: [
                          { name: "now", limit: 5 },
                          { name: "second", limit: 60, in_seconds: 1 },
                          { name: "minute", limit: 3600, in_seconds: 60 },
                          { name: "hour", limit: 86400, in_seconds: 3600  },
                          { name: "yesterday", limit: 172800, past_only: true },
                          { name: "tomorrow", limit: 172800, future_only: true },
                          { name: "day", limit: 604800, in_seconds: 86400 },
                          { name: "week", limit: 2629743, in_seconds: 604800  },
                          { name: "month", limit: 31556926, in_seconds: 2629743 },
                          { name: "year", limit: Infinity, in_seconds: 31556926 }
                      ]
            });
      });
    }

    function initInfiniteScrolling() {
      var $gallery = $('#gallery');
      $gallery.infinitescroll({
        navSelector  : '#page-nav',    // selector for the paged navigation
        nextSelector : '#page-nav a',  // selector for the NEXT link (to page 2)
        itemSelector : '.item',     // selector for all items you'll retrieve
        dataType     : 'json',
        loading      : {img: '/img/loading.gif'},
        destUrlCallback: function(destUrl) {
/*
          var query = $('.search-query').val()
          if (query.length > 0 && query != window.locale.search.default) {
            return destUrl + "?q=" + query
          } else {
            return destUrl
          }
*/
          if (typeof dyndata.next_page != 'undefined') {
            return destUrl + dyndata.next_page
          } else {
            return destUrl + "?q=" + dyndata.query + "&max_id=" + dyndata.max_id + "&result_type=" + dyndata.result_type
          }
        },
        template     : function(data) {
          dyndata = data
          var $div = $('<div/>')
            $.each(dyndata.results, function(i,x){
              var newElements = processItem(template, x)
              if (newElements != null) {
                $div.append(newElements);
              }
            });
            return $div.children();
          },
        },
        function( newElements ) {
          var $newElems = $( newElements ).css({ opacity: 0 });
          $newElems.imagesLoaded(function(){
            $newElems.animate({ opacity: 1 });
            $('#gallery').masonry('appended', $newElems, true );
            showTimeAgoDates();
          });

          var sels = $($newElems).filter(function(index) { return !!$(this).attr('data-sharable'); })
          if (inSelectMode()) {
            $(sels).each(function (idx, item) { attachItemSelectActions(item); });
          } else {
            $(sels).each(function (idx, item) {
              attachItemShareActions(item);
              attachItemTagActions(item);
            });
            $($newElems).each(function (idx, item) { enableItemLightbox(item); })
          }
        }
      );

    }

    function initGallery(data) {
      var $gallery = $('#gallery');

      $.each(data, function(i,x){
        var newElements = processItem(template, x);
        if (newElements != null) {
          $gallery = $gallery.append(newElements);
          $gallery.css({ opacity: 0 });
        }
      });

      $gallery.imagesLoaded(function(){
        $gallery.masonry({
          itemSelector : '.box',
          columnWidth: 230,
          cornerStampSelector: '.corner-stamp'
        });
        $gallery.animate({ opacity: 1 });
        initInfiniteScrolling();
       });

      enableGalleryClick();
      attachItemsShareActions();
      attachItemsTagActions();
      showTimeAgoDates();
    }

    initGallery(initdata.results);

    function disableGalleryClick() {
//      $('a[rel="gallery"]').unbind('click')
//      $('.ItemThumb').unbind('click')
//      $('.ItemThumb').click(function(e) { e.preventDefault(); });
//      $('.ItemThumb"]').removeAttr('rel')
      $('.item').each(function (idx,item) { disableItemLightbox(item); });
    }

    function enableGalleryClick() {
//      $('.ItemThumb').unbind('click')
//      $('.ItemThumb').attr('rel', 'gallery');
      $('.item').each(function (idx,item) { enableItemLightbox(item); });
    }

    function doDownload(url)
    {
        var iframe;
        iframe = document.getElementById("hiddenDownloader");
        if (iframe === null)
        {
            iframe = document.createElement('iframe');
            iframe.id = "hiddenDownloader";
            iframe.style.visibility = 'hidden';
            document.body.appendChild(iframe);
        }
        iframe.src = url;
    }

    function disableItemLightbox(item) {
      var thumb = $(item).find('.ItemThumb');
      $(thumb).unbind('click');
      $(thumb).click(function(e) { e.preventDefault(); })
    }

    function enableItemLightbox(item) {
      var thumb = $(item).find('.ItemThumb')
      $(thumb).unbind('click');
      $(thumb).click(function(e) {
        e.preventDefault();
        var downloadUrl = $(this).attr('href')
        $('#modal-lightbox').find('.modal-title').html($(thumb).attr('title'))
        $('#modal-lightbox').find('.modal-image').html($('<img/>').attr('src', $(thumb).find("img").attr('src')))
        $('#modal-lightbox').find('.modal-download').unbind('click')
        $('#modal-lightbox').find('.modal-download').click(function(e) {
            e.preventDefault();  //stop the browser from following
            doDownload(downloadUrl)
        })
        // fetch taggers
        // .modal-lightbox-taggers
        $.ajax({
            dataType: 'json',
            type: 'GET',
            url: '/taggers/'+$(item).attr("data-id"),
            success: function(data, textStatus, jqXHR) {
              if (data.length == 0) {
                $('.taggers-title').hide();
                return;
              }

              $('.taggers-title').show();
              $('.modal-lightbox-taggers').hide();
              $('.tagger-item-list tr').remove();
              var st = $('#template-tagger-item').html();
              $.each(data, function(i,tagger) {
                $('.tagger-item-list').append(Mustache.to_html(st, tagger));
              });
              $('.modal-lightbox-taggers').show();
            },
            error: function(jqXHR, textStatus, errorThrown) {
              searchState.loading = false
            }
        });


        $('#modal-lightbox').modal({dynamic: true});
        return false;
      })
    }

    function enableSelectNav() {
      disableSelectActions();
      $('.menu-row').show();
    }

    function disableSelectNav() {
      $('.menu-row').hide();
      disableSelectActions();
    }

    function inSelectMode() {
      return !$('.menu-row').is(':hidden');
    }

    function enableSelectActions() {
      $('.select-share-button').removeClass('disabled');
//      $('.select-edit-button').removeClass('disabled');
//      $('.select-delete-button').removeClass('disabled');
    }
    function disableSelectActions() {
      $('.select-share-button').addClass('disabled');
//      $('.select-edit-button').addClass('disabled');
//      $('.select-delete-button').addClass('disabled');
    }

    function haveSelectedItems() {
      return $("input[name='share[]']").filter(function(index) { return $(this).is(':checked'); }).size() > 0
    }

    function resetItemCheckboxes() {
      $("input[name='share[]']").prop('checked', false);
      $('.checkbox-img').attr("src", '/img/unchecked.png');
    }

    function clearSelectMode() {
      $(".icon-top-right").hide();
      $(".item").unbind('hover');
      $('.item').unbind('click');
      $('.item').removeClass('red');
      resetItemCheckboxes();
      enableGalleryClick();
      attachItemsShareActions();
      attachItemsTagActions();
      disableSelectNav();
    }

    $('.sharemsg').change(function() {
    });

    $('.sharemsg').live('keyup', function() {
      updateSharees();
    });

    $('.sharemsg').focus(function() {
      if ($('.sharemsg').val() === window.locale.fileupload.sharemsg) {
        $('.sharemsg').val('');
      };
    });

    $('.sharemsg').blur(function() {
      if ($('.sharemsg').val().length == 0) {
        $('.sharemsg').val(window.locale.fileupload.sharemsg);
      }
    });

    function resetShareModal() {
      $('.sharemsg').val(window.locale.fileupload.sharemsg);
      $('.share-item-list tr').remove();
      $('.sharee-list-wrapper').hide();
      $('.sharee-list').html('');
    }

    function resetTagModal() {
      $('.collect-tags').val("");
      $('.tag-item-list tr').remove();
    }

    resetShareModal(); // TODO: cluster all init code
    resetTagModal(); // TODO: cluster all init code

    function updateSharees() {
      var txt = $('.sharemsg').val();
      var list = txt.split(' ');
      var sharees = $.makeArray($(list).filter(function(idx) { return list[idx][0] == '@' && list[idx].length > 1; }));
      if (sharees.length > 0) {
        $('.sharee-list-wrapper').show();
        $('.sharee-list').html(sharees.join(' '));
      } else {
        $('.sharee-list-wrapper').hide();
        $('.sharee-list').html('');
      }
    }

    $('.select-share-button').click(function() {
      if ($('.select-share-button').hasClass('disabled')) return;
      var selected = $('.item').filter(function(index) { return $(this).find("input[name='share[]']").is(':checked'); })
      resetShareModal();
      var st = $('#template-share-item').html();
      $.each(selected, function(i,item) { addToShareList(item, st) });
      $('#modal-share').modal();
    });

    function addToShareList(item, st) {
      if (st == undefined) {
        st = $('#template-share-item').html();
      }
      var data = {};
      data['id'] = $(item).attr('data-id');
      data['name'] = $(item).find('.ItemThumb').attr('title');
      data['thumb'] = $(item).find('.ItemThumbImg').attr('src')
      $('.share-item-list').append(Mustache.to_html(st, data));
    }

    function addToTagList(item, st) {
      if (st == undefined) {
        st = $('#template-share-item').html();
      }
      var data = {};
      data['id'] = $(item).attr('data-id');
      data['name'] = $(item).find('.ItemThumb').attr('title');
      data['thumb'] = $(item).find('.ItemThumbImg').attr('src')
      $('.tag-item-list').append(Mustache.to_html(st, data));
    }

    function tagSubmit() {
      if ($('.collect-tags').val().length == 0) {
        alert("You didn't specifiy any tags.")
        return false;
      }

      var tags = $('.collect-tags').val();
      var ids = $.makeArray($('.tag-item-list').find('td[data-id]').map(function(idx,item) { return $(item).attr('data-id'); })).join(',')

      $('#modal-tag input[id="collect-ids"]').val(ids)
      $('#modal-tag input[id="collect-tags"]').val(tags)

      $.ajax({
        type: 'POST',
        url: '/tags',
        async: false,
        data: $("#form-tag-modal").serialize(),
        error: function() { alert('failed to tag!'); },
        dataType: 'json'
      });

      $('#modal-tag').modal('hide');
    }

    $("#form-tag-modal").submit(function() {
      tagSubmit();
      return false;
    });

    $('.tag-dialog-button').click(tagSubmit);

    $('.share-dialog-button').click(function() {
      if ($('.sharee-list').html().length == 0) {
        alert("You didn't specifiy any recipients.  Use @ notation to specify a Twitter user.")
        return false;
      }

      var rawSharees = $('.sharee-list').html().split(' ');
      var sharees = rawSharees.map(function(item){ if (item[0] == '@') { return item.substr(1); } else { return item; } })
      var ids = $.makeArray($('.share-item-list').find('td[data-id]').map(function(idx,item) { return $(item).attr('data-id'); })).join(',')

      $('#modal-share input[id="ids"]').val(ids)
      $('#modal-share input[id="sharees"]').val(sharees)

      $.ajax({
        type: 'POST',
        url: '/shares',
        async: false,
        data: $("#form-share-modal").serialize(),
        error: function() { alert('failed to share!'); },
        dataType: 'json'
      });

      $('#modal-share').modal('hide');
      clearSelectMode();
    });

    $('.select-cancel-button').click(function() {
      clearSelectMode();
    });

    $('.multi-select').click(function() {
      enableSelectNav();

      unattachItemsShareActions();
      unattachItemsTagActions();
      disableGalleryClick();

      resetItemCheckboxes();
      attachSelectActions();
    });

    function attachSelectActions() {
      $('.item[data-sharable="true"]').each(function (idx,item) { attachItemSelectActions(item); });
    }

    function attachItemSelectActions(item) {
//      $(item).find('a[rel="gallery"]').unbind('click')
      $(item).find('.ItemThumb').unbind('click')
      $(item).find('.ItemThumb').click(function(e) { e.preventDefault(); });
//      $(item).find('.ItemThumb"]').removeAttr('rel')
      $(item).find(".icon-top-right").show();
      $(item).hover(function(e) {$(this).addClass('red');}, function(e) {$(this).removeClass('red');});
      $(item).click(function(e) {
        var cd = $(this).find("input[name='share[]']");
        cd.prop('checked', !cd.is(':checked'))
        $(this).find('.checkbox-img').attr("src", cd.is(':checked') ? '/img/checked.png' : '/img/unchecked.png');
        if (cd.is(':checked')) {
          enableSelectActions();
        } else {
          if (!haveSelectedItems()) {
            disableSelectActions();
          }
        }
      });
    }

    $('.download-panel').draggable({ axis: "x", containment: 'parent', zIndex: 2700, scroll: false });

    // TODO: autoresizing
    $('.download-panel').css('top', $(document).height() - 340);
    $('.download-panel').css('left',$(document).width() - $('.download-panel').width() - 50)

    function toggleUploadCloseAll() {
      if ($('.upload-cancel-all-button').is(':hidden')) {
        $('.upload-cancel-all-button').show();
        $('.upload-button').hide();
    } else {
        $('.upload-button').show();
        $('.upload-cancel-all-button').hide();
      }
    }

    function toggleUploadPanelClosable() {
      if ($('.download-panel-close-icon').is(':hidden')) {
        $('.download-panel-close-icon').show();
      } else {
        $('.download-panel-close-icon').hide();
      }
    }

    $('.download-panel-close-icon').click(function() {
      $('.download-panel-close-icon').hide();
      $('.download-panel').hide();
      clearFileList();
    });

    $('.fileinput-button').click(function() {
      $('.fileinput-button').popover('hide');
    })

    $('.fileinput-button').popover({
      delay: 750, // { show: 750, hide: 100 },
      placement: 'bottom',
      title: "Add files...",
      content: "Add files by drag-n-drop anywhere on the page or by using the file dialog."
    })

    function applyTemplateToResults(data) {
      var $div = $('<div/>')
        $.each(data, function(i,x){
          var newElements = processItem(template, x)
          if (newElements != null) {
            $div.append(newElements);
          }
        });
      return $div.children();
    }

    var searchState = {
      loading: false,
      last: undefined
    }

    function appendNewelements(newElements) {
      var $newElems = $( newElements ).css({ opacity: 0 });
      $newElems.imagesLoaded(function(){
        $newElems.animate({ opacity: 1 });
        $('#gallery').masonry('reload');
        showTimeAgoDates();
      });
      $('#gallery').append($newElems)

      var sels = $($newElems).filter(function(index) { return !!$(this).attr('data-sharable'); })
      if (inSelectMode()) {
        $(sels).each(function (idx, item) { attachItemSelectActions(item); });
      } else {
        $(sels).each(function (idx, item) {
          attachItemShareActions(item);
          attachItemTagActions(item);
        });
        $($newElems).each(function (idx, item) { enableItemLightbox(item); })
      }
    }

    function onSearch() {
      if (searchState.loading) return

      var tags = $('.search-query').val();
      if (tags == undefined && tags.length == 0) return
//      if (tags === searchState.last) return

      searchState.last = tags
      var network = $('.network-selector .active').attr('id') == "network-my";

      $.ajax({
          dataType: 'json',
          type: 'GET',
          url: '/j/0',
          data: {
            'q': tags,
            'network': network
          },
          success: function(data, textStatus, jqXHR) {
            dyndata = data
            var results = dyndata.results
            if (results.length > 0) {
              var $newElems = applyTemplateToResults(results);
              $('#gallery').children().remove();
              appendNewelements($newElems);
              initInfiniteScrolling();
              searchState.loading = false
            }
          },
          error: function(jqXHR, textStatus, errorThrown) {
            searchState.loading = false
          }
      });
    }

    function resetSearchBox() {
      $('.search-query').val(window.locale.search.default);
    }

    function initSearch() {
      resetSearchBox();

      $('.search-icon').click(function() {
        onSearch();
        return false;
      })

      $('.navbar-search').submit(function() {
        onSearch();
        return false;
      })

      $('.search-query').change(function() {
//        onSearch();
        return false;
      });

      $('.search-query').live('keyup', function() {
//        onSearch();
        return false;
      });

      $('.search-query').focus(function() {
        var query = $('.search-query').val();
        if (query != undefined && query == window.locale.search.default) {
          $('.search-query').val("");
        }
      });

      $('.search-query').blur(function() {
        var query = $('.search-query').val();
        if (query != undefined && query.length == 0) {
          resetSearchBox();
        }
      });
    }

    initSearch();

    // center main
    function alignMain() {
      var k;
      var width = document.width
      if (typeof width == 'undefined') {
        width = window.screen.width
      }
      if (document.width <= 1200) {
        k = 27;
      } else {
        k = 64;
      }
      $('.main-content-row').attr('style', 'margin-left: ' + width * k / 1280  +'px');
    }

    alignMain();

    function processItem(template, item) {
      // convert instagram and twitpic media links
      // resolve media url conflicts (when multiple)
      item.isSharable = true
      if (typeof item.entities.media != 'undefined' && item.entities.media != null) {
        var media = item.entities.media[0]
        item.mediaUrl = media.media_url_https
        item.expandedUrl = media.url
        item.displayUrl = media.display_url
      } else {
        $.each(item.entities.urls, function(i,x){
          if (x.expanded_url.indexOf("instagr.am") >= 0) {
            // extract code and create direct media url
            // http://instagram.com/p/QSRtwvO2_T/media
            var code = x.expanded_url.split("/")[4]
            item.mediaUrl = "http://instagram.com/p/" + code + "/media"
            item.expandedUrl = x.url
            item.displayUrl = x.display_url
            return false
          } else if (x.expanded_url.indexOf("twitpic") >= 0) {
            // extract code and create direct media url
            // http://twitpic.com/show/full/b007dk
            var code = x.expanded_url.split("/")[3]
            item.mediaUrl = "http://twitpic.com/show/full/" + code
            item.expandedUrl = x.url
            item.displayUrl = x.display_url
            return false
          } else {
/*
            item.mediaUrl = x.expanded_url
            item.expandedUrl = x.url
            item.displayUrl = x.display_url
*/
          }
        });
      }

      if (typeof item.mediaUrl == 'undefined') {
        return null
      }

      return Mustache.to_html(template, item)
    }
};

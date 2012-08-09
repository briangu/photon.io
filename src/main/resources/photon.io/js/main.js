var snapclearApp = function (initdata) {
    'use strict';

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

/*
    $('#fileupload').bind('fileuploadfail', function (e, data) {
      if ($('.upload-file-queue tr').size() == 1) {
        enableUpload(false);
      }
    });
*/

    $('#fileupload').bind('fileuploadstop', function (e, data) {
      if ($('.upload-file-queue tr').size() == 1) {
        enableUpload(false);
      }
      toggleUploadCloseAll();
      toggleUploadPanelClosable();
    });

    var template = $('#template-mason-brick').html();

    $('#fileupload').bind('fileuploaddone', function (e, data) {
      var x = JSON.parse(data.jqXHR.responseText)[0]
      var newElements = Mustache.to_html(template, x);
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

    initUI();

    function showTimeAgoDates() {
      $(".easydate").each(function() {
        $(this).html($.easydate.format_date(new Date(parseInt($(this).attr("data-filedate"), 10))));
      });
    }

    function initGallery(data) {
      var $gallery = $('#gallery');

      $.each(initdata, function(i,x){
        var newElements = Mustache.to_html(template, x);
        $gallery = $gallery.append(newElements);
        $gallery.css({ opacity: 0 });
      });

      $gallery.imagesLoaded(function(){
        $gallery.masonry({
          itemSelector : '.box',
          columnWidth: 230,
          cornerStampSelector: '.corner-stamp'
        });
        $gallery.animate({ opacity: 1 });
      });

      $gallery.infinitescroll({
        navSelector  : '#page-nav',    // selector for the paged navigation
        nextSelector : '#page-nav a',  // selector for the NEXT link (to page 2)
        itemSelector : '.item',     // selector for all items you'll retrieve
        dataType     : 'json',
        loading      : {img: '/img/transparent.png'},
        destUrlCallback: function(destUrl) {
          var query = $('.search-query').val()
          if (query.length > 0 && query != window.locale.search.default) {
            return destUrl + "?q=" + query
          } else {
            return destUrl
          }
        },
        template     : function(data) {
          var $div = $('<div/>')
            $.each(data, function(i,x){
              $div.append(Mustache.to_html(template, x));
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
            $(sels).each(function (idx, item) { attachItemShareActions(item); });
          }
          $($newElems).each(function (idx, item) { enableItemLightbox(item); })
        }
      );

      enableGalleryClick();
      attachItemsShareActions();
      showTimeAgoDates();
    }

    initGallery(initdata);

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
        $('#modal-lightbox').modal();
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

    resetShareModal(); // TODO: cluster all init code

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
          $div.append(Mustache.to_html(template, x));
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
        $(sels).each(function (idx, item) { attachItemShareActions(item); });
      }
      $($newElems).each(function (idx, item) { enableItemLightbox(item); })
    }

    function onSearch() {
      if (searchState.loading) return

      var tags = $('.search-query').val();
      if (tags == undefined && tags.length == 0) return
      if (tags === searchState.last) return

      searchState.last = tags

      $.ajax({
          dataType: 'json',
          type: 'GET',
          url: '/j/0',
          data: {'q': tags},
          success: function(data, textStatus, jqXHR) {
            if (data.length > 0) {
              var $newElems = applyTemplateToResults(data);
              $('#gallery').children().remove();
              appendNewelements($newElems)
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

      $('.search-query').change(function() {
        onSearch();
        return false;
      });

      $('.search-query').live('keyup', function() {
        onSearch();
        return false;
      });

      $('.search-query').focus(function() {
        $('.search-query').val("");
      });

      $('.search-query').blur(function() {
        var query = $('.search-query').val();
        if (query != undefined && query.length == 0) {
          resetSearchBox();
        }
      });
    }

    initSearch();
};
